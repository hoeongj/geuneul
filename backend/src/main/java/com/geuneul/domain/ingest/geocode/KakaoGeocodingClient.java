package com.geuneul.domain.ingest.geocode;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * 카카오 로컬 "주소 검색" API (https://developers.kakao.com/docs/latest/ko/local/dev-guide).
 * GET /v2/local/search/address.json?query=... , 헤더 Authorization: KakaoAK {REST 키}.
 *
 * - 도로명(road_address) 우선, 없으면 지번(address) 좌표. 카카오 응답의 x=경도, y=위도.
 * - 429(쿼터/속도 초과)는 백오프 재시도 3회. 그 외 오류는 empty 처리(호출부에서 실패 계수).
 * - 키는 환경변수 KAKAO_REST_API_KEY (규칙 D: 코드/레포에 하드코딩 금지).
 */
@Component
public class KakaoGeocodingClient implements GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoGeocodingClient.class);
    private static final int MAX_RETRY = 3;

    private final RestClient restClient;
    private final boolean keyPresent;

    public KakaoGeocodingClient(@Value("${kakao.rest-api-key:}") String restApiKey) {
        this.keyPresent = restApiKey != null && !restApiKey.isBlank();
        // RestClient.Builder 빈 주입 대신 정적 빌더 — Boot 4는 RestClient 자동구성이 별도 모듈로
        // 분리되어 기본 클래스패스에 Builder 빈이 없다(전 IT 컨텍스트 생성 실패의 원인, WORKLOG).
        // 배치 전용 클라이언트라 자동구성(관측성 계측 등) 이점보다 의존 단순화가 낫다.
        this.restClient = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }

    @Override
    public Optional<LatLng> geocode(String address) {
        if (!keyPresent) {
            throw new IllegalStateException(
                    "KAKAO_REST_API_KEY가 없습니다. 지오코딩이 필요한 소스는 키 설정 후 실행하세요 (규칙 D: .env/SSM으로만).");
        }
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                JsonNode body = restClient.get()
                        .uri(uri -> uri.path("/v2/local/search/address.json")
                                .queryParam("query", address)
                                .queryParam("size", 1)
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode documents = body == null ? null : body.get("documents");
                if (documents == null || documents.isEmpty()) {
                    return Optional.empty();
                }
                JsonNode doc = documents.get(0);
                // 도로명 매칭 우선 — 표준데이터 주소가 도로명 기준이라 정확도가 높다.
                JsonNode target = doc.hasNonNull("road_address") ? doc.get("road_address") : doc;
                double lng = target.get("x").asDouble();
                double lat = target.get("y").asDouble();
                return Optional.of(new LatLng(lat, lng));
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(429)) && attempt < MAX_RETRY) {
                    sleep(500L * attempt); // rate limit 백오프
                    continue;
                }
                log.warn("[geocode] 실패({}): {}", e.getStatusCode(), address);
                return Optional.empty();
            } catch (Exception e) {
                log.warn("[geocode] 오류: {} — {}", address, e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
