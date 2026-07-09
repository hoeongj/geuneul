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
 * - 오류/빈 페이지는 예외를 던지지 않고 {@link LibraryPage#empty()}로 처리 — 호출부가 페이지네이션
 *   종료 신호로 쓴다(정상 종료인 NODATA_ERROR와 진짜 오류를 굳이 구분하지 않는다 — 둘 다 "이 페이지는
 *   못 쓴다"는 점에서 동일하게 다뤄도 안전, 대신 오류는 로그로 남긴다).
 */
@Component
public class DataGoKrPublicLibraryClient implements PublicLibraryApiClient {

    private static final Logger log = LoggerFactory.getLogger(DataGoKrPublicLibraryClient.class);
    private static final String PATH = "/openapi/tn_pubr_public_lbrry_api";

    private final RestClient restClient;
    private final String serviceKey;
    private final boolean keyPresent;

    @Autowired
    public DataGoKrPublicLibraryClient(@Value("${datago.service-key:}") String serviceKey) {
        this(serviceKey, RestClient.builder());
    }

    /** 테스트용 — MockRestServiceServer를 바인딩한 builder를 주입한다. */
    DataGoKrPublicLibraryClient(String serviceKey, RestClient.Builder builder) {
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
                return LibraryPage.empty();
            }
            LibraryApiResponse.Header header = body.response().header();
            if (header == null || !"00".equals(header.resultCode())) {
                if (header != null && !"03".equals(header.resultCode())) {
                    // 03(NODATA_ERROR)은 "다음 페이지 없음"의 정상 신호 — 그 외만 경고.
                    log.warn("[library-api] 비정상 응답 page={} resultCode={} msg={}",
                            pageNo, header.resultCode(), header.resultMsg());
                }
                return LibraryPage.empty();
            }
            LibraryApiResponse.Body data = body.response().body();
            if (data == null || data.items() == null) {
                return LibraryPage.empty();
            }
            return new LibraryPage(data.items(), parseIntSafe(data.totalCount()));
        } catch (Exception e) {
            log.warn("[library-api] 호출 실패 page={}: {}", pageNo, e.getMessage());
            return LibraryPage.empty();
        }
    }

    private static int parseIntSafe(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
