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
 * 오류/빈 페이지는 예외 없이 {@link ShelterPage#empty()}로 처리해 호출부가 페이지네이션 종료 신호로 쓴다
 * ({@link SafetyDataShelterClient}은 도서관 클라이언트와 동일한 관대한 종료 규약).
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
                return ShelterPage.empty();
            }
            ShelterApiResponse.Header header = body.header();
            if (header == null || !"00".equals(header.resultCode())) {
                if (header != null) {
                    log.warn("[shelter-api] 비정상 응답 page={} resultCode={} msg={}",
                            pageNo, header.resultCode(), header.resultMsg());
                }
                return ShelterPage.empty();
            }
            if (body.body() == null) {
                return ShelterPage.empty();
            }
            return new ShelterPage(body.body(), body.totalCount() == null ? 0 : body.totalCount());
        } catch (Exception e) {
            log.warn("[shelter-api] 호출 실패 page={}: {}", pageNo, e.getMessage());
            return ShelterPage.empty();
        }
    }
}
