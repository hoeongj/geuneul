package com.geuneul.domain.ingest.openapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 전국도서관표준데이터 오픈API({@code tn_pubr_public_lbrry_api}, 문화체육관광부/data.go.kr).
 * 2026-07-09 실측: 페이지네이션(pageNo/numOfRows)만으로 전국 3,555건을 지역 파라미터 없이 순회
 * 가능 — 상권정보 API(storeListInDong류, 행정동코드 필요)보다 훨씬 단순하다.
 *
 * - serviceKey는 data.go.kr 계정 공통 인증키(디코딩 형태, 특수문자 없음 — 날씨 KMA_SERVICE_KEY와
 *   같은 값). 규칙 D: 코드에 하드코딩 금지, 환경변수로만.
 * - 03(NODATA_ERROR)만 정상 페이지 종료로 취급한다. HTTP·파싱·비정상 응답은 값 비노출 예외로 실패시켜
 *   부분 스냅샷이 성공으로 기록되거나 stale 도서관을 비활성화하지 못하게 한다.
 */
@Component
public class DataGoKrPublicLibraryClient implements PublicLibraryApiClient {

    private static final Logger log = LoggerFactory.getLogger(DataGoKrPublicLibraryClient.class);
    private static final String PATH = "/openapi/tn_pubr_public_lbrry_api";

    private final RestClient restClient;
    private final String serviceKey;
    private final boolean keyPresent;

    @Autowired
    public DataGoKrPublicLibraryClient(@Value("${datago.service-key:}") String serviceKey,
                                       RestClient.Builder builder) {
        this.serviceKey = serviceKey;
        this.keyPresent = serviceKey != null && !serviceKey.isBlank();
        this.restClient = builder.baseUrl("https://api.data.go.kr").build();
    }

    @Override
    public LibraryPage fetchPage(int pageNo, int numOfRows) {
        if (!keyPresent) {
            throw new IllegalStateException(
                    "DATA_GO_KR_SERVICE_KEY가 없습니다. 도서관 오픈API 인제스천은 키 설정 후 실행하세요 (규칙 D).");
        }
        try {
            LibraryApiResponse body = restClient.get()
                    .uri(uri -> uri.path(PATH)
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("type", "json")
                            .build())
                    .retrieve()
                    .body(LibraryApiResponse.class);

            if (body == null || body.response() == null) {
                throw new LibraryApiException(pageNo, "응답 envelope 누락");
            }
            LibraryApiResponse.Header header = body.response().header();
            String resultCode = safeResultCode(header == null ? null : header.resultCode());
            if ("03".equals(resultCode)) {
                return LibraryPage.empty();
            }
            if (!"00".equals(resultCode)) {
                log.warn("[library-api] 비정상 응답 page={} resultCode={}", pageNo, resultCode);
                throw new LibraryApiException(pageNo, "비정상 resultCode=" + resultCode);
            }
            LibraryApiResponse.Body data = body.response().body();
            if (data == null || data.items() == null) {
                throw new LibraryApiException(pageNo, "응답 body/items 누락");
            }
            return new LibraryPage(data.items(), parsePositiveTotal(data.totalCount(), data.items().isEmpty(), pageNo));
        } catch (LibraryApiException e) {
            throw e;
        } catch (Exception e) {
            // RestClient 예외 메시지에는 serviceKey가 포함된 요청 URI가 들어갈 수 있어 타입만 남긴다.
            log.warn("[library-api] 호출 실패 page={} type={}", pageNo, e.getClass().getSimpleName());
            throw new LibraryApiException(pageNo, "HTTP 또는 응답 파싱 오류");
        }
    }

    private static int parsePositiveTotal(String value, boolean emptyItems, int pageNo) {
        if (value == null || value.isBlank()) {
            throw new LibraryApiException(pageNo, "totalCount 누락");
        }
        try {
            int total = Integer.parseInt(value.trim());
            if (total < 0 || (total == 0 && !emptyItems)) {
                throw new LibraryApiException(pageNo, "유효하지 않은 totalCount");
            }
            return total;
        } catch (NumberFormatException e) {
            throw new LibraryApiException(pageNo, "totalCount 형식 오류");
        }
    }

    private static String safeResultCode(String value) {
        if (value == null || value.isBlank()) {
            return "missing";
        }
        String trimmed = value.trim();
        return trimmed.matches("[0-9]{2,4}") ? trimmed : "invalid";
    }
}
