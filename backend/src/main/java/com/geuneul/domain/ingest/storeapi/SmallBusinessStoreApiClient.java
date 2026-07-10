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
 * <p><b>계약 검증 완료(2026-07-10, TS-026)</b> — 활용신청 승인 후 실 호출로 확정했다. 응답은
 * {@code response} 래퍼 없이 {@code header}·{@code body}가 최상위({@link StoreApiResponse}), 좌표·
 * 페이지네이션은 숫자다. {@code indsSclsCd} 서버측 필터로 대상 업종만 받는다. 파라미터/응답이 또
 * 바뀌면 이 클래스만 고치면 된다(도메인 서비스는 {@link StoreApiClient}/{@link StorePage} 추상화만 의존).
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
    public StorePage searchByRadius(double lat, double lng, int radiusMeters, String indsSclsCd,
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
                        if (indsSclsCd != null && !indsSclsCd.isBlank()) {
                            b.queryParam("indsSclsCd", indsSclsCd);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(StoreApiResponse.class);

            if (body == null) {
                return StorePage.empty();
            }
            StoreApiResponse.Header header = body.header();
            if (header == null || !"00".equals(header.resultCode())) {
                // "03" = NODATA(정상적 빈 결과)라 소음 로그를 남기지 않는다. 그 외만 warn.
                if (header != null && !"03".equals(header.resultCode())) {
                    log.warn("[store-api] 비정상 응답 page={} resultCode={} msg={}",
                            pageNo, header.resultCode(), header.resultMsg());
                }
                return StorePage.empty();
            }
            StoreApiResponse.Body data = body.body();
            if (data == null || data.items() == null) {
                return StorePage.empty();
            }
            return new StorePage(data.items(), data.totalCount() == null ? 0 : data.totalCount());
        } catch (Exception e) {
            log.warn("[store-api] 호출 실패 page={}: {}", pageNo, e.getMessage());
            return StorePage.empty();
        }
    }
}
