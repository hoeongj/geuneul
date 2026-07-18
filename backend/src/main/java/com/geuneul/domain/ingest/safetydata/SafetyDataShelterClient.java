package com.geuneul.domain.ingest.safetydata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 행정안전부_무더위쉼터 오픈API(safetydata.go.kr V2, {@code DSSP-IF-10942}) 실 구현체.
 *
 * <p><b>계약 검증 완료(2026-07-10, TS-027)</b> — safetydata 전용 서비스키로 실호출 확정(전국 60,297건).
 * safetydata는 data.go.kr과 <b>별개 포털·별개 서비스키</b>(datago 키로는 resultCode 30). 키는
 * {@code ${safetydata.service-key}}(env {@code SAFETYDATA_SERVICE_KEY})로만 주입한다 — 규칙 D, 하드코딩 금지.
 * HTTP·파싱·비정상 응답은 값 비노출 예외로 실패시켜 부분 snapshot을 정상 종료로 오인하지 않는다.
 */
@Component
public class SafetyDataShelterClient implements SafetyDataApiClient {

    private static final Logger log = LoggerFactory.getLogger(SafetyDataShelterClient.class);
    private static final String PATH = "/V2/api/DSSP-IF-10942";

    private final RestClient restClient;
    private final String serviceKey;
    private final boolean keyPresent;

    @Autowired
    public SafetyDataShelterClient(@Value("${safetydata.service-key:}") String serviceKey,
                                   RestClient.Builder builder) {
        this.serviceKey = serviceKey;
        this.keyPresent = serviceKey != null && !serviceKey.isBlank();
        this.restClient = builder.baseUrl("https://www.safetydata.go.kr").build();
    }

    @Override
    public ShelterPage fetchPage(int pageNo, int numOfRows) {
        if (!keyPresent) {
            throw new IllegalStateException(
                    "SAFETYDATA_SERVICE_KEY가 없습니다. 무더위쉼터 인제스천은 키 설정 후 실행하세요 (규칙 D). "
                            + "safetydata.go.kr 전용 키(data.go.kr 키와 별개)입니다.");
        }
        try {
            ShelterApiResponse body = restClient.get()
                    .uri(uri -> uri.path(PATH)
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("returnType", "json")
                            .build())
                    .retrieve()
                    .body(ShelterApiResponse.class);

            if (body == null) {
                throw new ShelterApiException(pageNo, "응답 envelope 누락");
            }
            ShelterApiResponse.Header header = body.header();
            String resultCode = safeResultCode(header == null ? null : header.resultCode());
            if ("03".equals(resultCode)) {
                return ShelterPage.empty();
            }
            if (!"00".equals(resultCode)) {
                log.warn("[shelter-api] 비정상 응답 page={} resultCode={}", pageNo, resultCode);
                throw new ShelterApiException(pageNo, "비정상 resultCode=" + resultCode);
            }
            if (body.body() == null) {
                throw new ShelterApiException(pageNo, "응답 body 누락");
            }
            int totalCount = body.totalCount() == null ? -1 : body.totalCount();
            if (totalCount < 0 || (totalCount == 0 && !body.body().isEmpty())) {
                throw new ShelterApiException(pageNo, "유효하지 않은 totalCount");
            }
            return new ShelterPage(body.body(), totalCount);
        } catch (ShelterApiException e) {
            throw e;
        } catch (Exception e) {
            // RestClient 예외 URI에는 serviceKey가 포함될 수 있어 타입 외 값을 로그에 남기지 않는다.
            log.warn("[shelter-api] 호출 실패 page={} type={}", pageNo, e.getClass().getSimpleName());
            throw new ShelterApiException(pageNo, "HTTP 또는 응답 파싱 오류");
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
