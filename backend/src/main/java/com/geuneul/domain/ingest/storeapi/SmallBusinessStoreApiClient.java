package com.geuneul.domain.ingest.storeapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 소상공인시장진흥공단 상가(상권)정보 오픈API(반경 검색) 실 구현체.
 *
 * ⚠️ <b>계약 미검증(ADR-0006)</b> — 엔드포인트·파라미터명은 공식 매뉴얼(data.sbiz.or.kr)·서드파티
 * 가이드 리서치 기반이고, "상가업소정보" 활용신청이 아직 미승인(2026-07-09 실측 403)이라 실 호출로
 * 확증하지 못했다. 승인 후 반드시 재검증 — 파라미터/응답 필드가 다르면 이 클래스만 고치면 된다
 * (도메인 서비스는 {@link StoreApiClient}/{@link StorePage} 추상화만 의존).
 */
@Component
public class SmallBusinessStoreApiClient implements StoreApiClient {

    private static final Logger log = LoggerFactory.getLogger(SmallBusinessStoreApiClient.class);
    private static final String PATH = "/B553077/api/open/sdsc2/storeListInRadius";

    private final RestClient restClient;
    private final String serviceKey;
    private final boolean keyPresent;

    @Autowired
    public SmallBusinessStoreApiClient(@Value("${datago.service-key:}") String serviceKey) {
        this(serviceKey, RestClient.builder());
    }

    /** 테스트용 — MockRestServiceServer를 바인딩한 builder를 주입한다. */
    SmallBusinessStoreApiClient(String serviceKey, RestClient.Builder builder) {
        this.serviceKey = serviceKey;
        this.keyPresent = serviceKey != null && !serviceKey.isBlank();
        this.restClient = builder.baseUrl("https://apis.data.go.kr").build();
    }

    @Override
    public StorePage searchByRadius(double lat, double lng, int radiusMeters, String indsLclsCd,
                                    int pageNo, int numOfRows) {
        if (!keyPresent) {
            throw new IllegalStateException(
                    "DATA_GO_KR_SERVICE_KEY가 없습니다. 상권정보 인제스천은 키 설정 후 실행하세요 (규칙 D).");
        }
        try {
            StoreApiResponse body = restClient.get()
                    .uri(uri -> {
                        var b = uri.path(PATH)
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("radius", radiusMeters)
                                .queryParam("cx", lng)
                                .queryParam("cy", lat)
                                .queryParam("type", "json")
                                .queryParam("numOfRows", numOfRows)
                                .queryParam("pageNo", pageNo);
                        if (indsLclsCd != null && !indsLclsCd.isBlank()) {
                            b.queryParam("indsLclsCd", indsLclsCd);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(StoreApiResponse.class);

            if (body == null || body.response() == null) {
                return StorePage.empty();
            }
            StoreApiResponse.Header header = body.response().header();
            if (header == null || !"00".equals(header.resultCode())) {
                if (header != null && !"03".equals(header.resultCode())) {
                    log.warn("[store-api] 비정상 응답 page={} resultCode={} msg={}",
                            pageNo, header.resultCode(), header.resultMsg());
                }
                return StorePage.empty();
            }
            StoreApiResponse.Body data = body.response().body();
            if (data == null || data.items() == null) {
                return StorePage.empty();
            }
            return new StorePage(data.items(), parseIntSafe(data.totalCount()));
        } catch (Exception e) {
            log.warn("[store-api] 호출 실패 page={}: {}", pageNo, e.getMessage());
            return StorePage.empty();
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
