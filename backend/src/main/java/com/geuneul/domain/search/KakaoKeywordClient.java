package com.geuneul.domain.search;

import com.geuneul.domain.search.dto.PlaceSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 카카오 로컬 "키워드로 장소 검색" API (N5, https://developers.kakao.com/docs/latest/ko/local/dev-guide).
 * GET /v2/local/search/keyword.json?query=... , 헤더 Authorization: KakaoAK {REST 키}.
 *
 * <p>지오코딩(KakaoGeocodingClient)과 <b>같은 도메인·같은 REST 키</b>를 쓴다 — 실호출 계약검증으로 인가 확인
 * (TS-026, 2026-07-11: keyword.json HTTP200, documents[].x=경도·y=위도 문자열). 키는 이미 SSM에 배선돼 있어
 * 새 비밀·사용자 액션이 없다. 응답은 타입 있는 record로 역직렬화한다(Boot 4=Jackson 3, JsonNode 금지 — TS-004/028).
 *
 * <p>실패(키 없음·4xx·네트워크)는 <b>빈 리스트</b>로 흘린다(graceful) — 검색은 부가기능이라 500으로 화면을
 * 깨지 않고 "결과 없음"으로 처리한다. 좌표가 오면 sort=distance로 내 주변을 우선한다("성균관대" 등 동명 다수 대응).
 */
@Component
public class KakaoKeywordClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoKeywordClient.class);

    /** 카카오 키워드 검색 페이지 크기 상한(문서상 최대 15). */
    static final int MAX_SIZE = 15;

    /** 카카오 키워드검색 응답(필요 필드만). x=경도, y=위도(문자열). */
    record KakaoKeywordResponse(List<Document> documents) {
        record Document(String place_name, String address_name, String road_address_name,
                        String x, String y, String category_group_name) {
        }
    }

    private final RestClient restClient;
    private final boolean keyPresent;

    // 생성자 2개(운영/테스트) — @Autowired로 운영 생성자를 명시(없으면 컨텍스트 생성 실패, KakaoGeocodingClient와 동일).
    @Autowired
    public KakaoKeywordClient(@Value("${kakao.rest-api-key:}") String restApiKey) {
        this(restApiKey, RestClient.builder());
    }

    /** 테스트용 — MockRestServiceServer를 바인딩한 builder로 실 파싱 경로를 검증한다. */
    KakaoKeywordClient(String restApiKey, RestClient.Builder builder) {
        this.keyPresent = restApiKey != null && !restApiKey.isBlank();
        this.restClient = builder
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }

    /**
     * 키워드로 장소를 검색한다. lat/lng가 오면 그 좌표 기준 거리순으로 정렬한다(위치 바이어스).
     * @return 최대 size건. 실패·무키면 빈 리스트(graceful).
     */
    public List<PlaceSearchResult> search(String query, Double lat, Double lng, int size) {
        if (!keyPresent) {
            log.warn("[search] KAKAO_REST_API_KEY 미설정 — 빈 결과 반환(규칙 D: .env/SSM으로만).");
            return List.of();
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        boolean bias = lat != null && lng != null;
        try {
            KakaoKeywordResponse body = restClient.get()
                    .uri(uri -> {
                        uri.path("/v2/local/search/keyword.json")
                                .queryParam("query", query)
                                .queryParam("size", safeSize);
                        // sort=distance는 x(경도)·y(위도)가 반드시 함께 있어야 카카오가 받아준다 → 좌표 있을 때만.
                        if (bias) {
                            uri.queryParam("x", lng).queryParam("y", lat).queryParam("sort", "distance");
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(KakaoKeywordResponse.class);

            if (body == null || body.documents() == null) {
                return List.of();
            }
            return body.documents().stream()
                    .map(KakaoKeywordClient::toResult)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("[search] 카카오 키워드 검색 실패: '{}' — {}", query, e.getMessage());
            return List.of();
        }
    }

    /** 카카오 문서 1건 → 우리 계약. 좌표 파싱 실패 행은 버린다(null → filter). x=경도, y=위도. */
    private static PlaceSearchResult toResult(KakaoKeywordResponse.Document d) {
        if (d == null || d.x() == null || d.y() == null) {
            return null;
        }
        try {
            double lng = Double.parseDouble(d.x());
            double lat = Double.parseDouble(d.y());
            return new PlaceSearchResult(d.place_name(), d.address_name(), d.road_address_name(),
                    lat, lng, d.category_group_name());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
