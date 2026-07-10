package com.geuneul.domain.route;

import com.geuneul.domain.route.dto.LatLng;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 도로 경로 폴리라인 공급자(F3, ADR-0019/0021) — 카카오모빌리티 다중경유지 길찾기로 실제 도로를 따르는 폴리라인을 만든다.
 *
 * <p>엔드포인트: {@code POST https://apis-navi.kakaomobility.com/v1/waypoints/directions},
 * 헤더 {@code Authorization: KakaoAK {REST 키}}. 키는 지오코딩/로그인과 <b>같은 카카오 REST 키</b>를 재사용한다
 * ({@code kakao.rest-api-key}, env {@code KAKAO_REST_API_KEY}) — 실호출로 이 키가 navi 엔드포인트에 인가됨을
 * 계약검증했다(TS-026, 2026-07-11). 별도 카카오모빌리티 앱 발급 불필요.
 *
 * <p><b>전략 교체점</b>: {@link Primary}로 등록돼 키가 있으면 {@link RouteService}가 이 공급자를 쓴다(mode="road").
 * 키가 없으면(로컬/CI) {@link #polyline}이 <b>HTTP 호출 없이 즉시 empty</b>를 반환해 RouteService가 직선으로
 * 폴백한다(mode="straight" — 기존 계약·IT 유지). 런타임 실패(네트워크·쿼터·결측)도 empty → 직선 폴백(graceful).
 *
 * <p>응답 파싱: {@code routes[0].sections[].roads[].vertexes}(평탄 [경도,위도,경도,위도,…] 쌍)를 순서대로
 * 이어 붙인다. Boot 4(Jackson 3)에서 JsonNode는 타입오류(TS-004)라 타입 있는 record로 역직렬화한다.
 */
@Component
@Primary
public class KakaoDirectionsProvider implements DirectionsProvider {

    private static final Logger log = LoggerFactory.getLogger(KakaoDirectionsProvider.class);

    /** 카카오 waypoints 길찾기 응답(필요 필드만). vertexes = [경도,위도,…] 평탄 배열. */
    record KakaoDirectionsResponse(List<Route> routes) {
        record Route(Integer result_code, String result_msg, List<Section> sections) {
        }

        record Section(List<Road> roads) {
        }

        record Road(List<Double> vertexes) {
        }
    }

    /** 요청 바디 — x=경도, y=위도. priority=RECOMMEND(추천). */
    record DirectionsRequest(Point origin, Point destination, List<Point> waypoints, String priority) {
        record Point(double x, double y) {
        }
    }

    private final RestClient restClient;
    private final boolean keyPresent;

    @Autowired
    public KakaoDirectionsProvider(@Value("${kakao.rest-api-key:}") String restApiKey) {
        this(restApiKey, RestClient.builder());
    }

    /** 테스트용 — MockRestServiceServer 바인딩 builder를 주입해 실제 파싱 경로를 검증한다. */
    KakaoDirectionsProvider(String restApiKey, RestClient.Builder builder) {
        this.keyPresent = restApiKey != null && !restApiKey.isBlank();
        this.restClient = builder
                .baseUrl("https://apis-navi.kakaomobility.com")
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }

    @Override
    public Optional<List<LatLng>> polyline(List<LatLng> waypoints) {
        // 키 없음(로컬/CI) → HTTP 없이 즉시 폴백. origin+destination 최소 2점 필요.
        if (!keyPresent || waypoints == null || waypoints.size() < 2) {
            return Optional.empty();
        }
        LatLng origin = waypoints.get(0);
        LatLng destination = waypoints.get(waypoints.size() - 1);
        List<DirectionsRequest.Point> mid = new ArrayList<>();
        for (int i = 1; i < waypoints.size() - 1; i++) {
            mid.add(new DirectionsRequest.Point(waypoints.get(i).lng(), waypoints.get(i).lat()));
        }
        DirectionsRequest body = new DirectionsRequest(
                new DirectionsRequest.Point(origin.lng(), origin.lat()),
                new DirectionsRequest.Point(destination.lng(), destination.lat()),
                mid, "RECOMMEND");

        try {
            KakaoDirectionsResponse res = restClient.post()
                    .uri("/v1/waypoints/directions")
                    .body(body)
                    .retrieve()
                    .body(KakaoDirectionsResponse.class);

            List<LatLng> line = extractPolyline(res);
            return line.size() >= 2 ? Optional.of(line) : Optional.empty();
        } catch (Exception e) {
            log.warn("[route] 카카오 도로 길찾기 실패 — 직선 폴백: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** routes[0].sections[].roads[].vertexes([경도,위도,…])를 순서대로 이어 LatLng 폴리라인으로. 결측이면 빈 리스트. */
    static List<LatLng> extractPolyline(KakaoDirectionsResponse res) {
        List<LatLng> line = new ArrayList<>();
        if (res == null || res.routes() == null || res.routes().isEmpty()) {
            return line;
        }
        KakaoDirectionsResponse.Route route = res.routes().get(0);
        if (route.result_code() == null || route.result_code() != 0 || route.sections() == null) {
            return line; // 길찾기 실패(result_code!=0) → 폴백
        }
        for (KakaoDirectionsResponse.Section section : route.sections()) {
            if (section.roads() == null) {
                continue;
            }
            for (KakaoDirectionsResponse.Road road : section.roads()) {
                List<Double> v = road.vertexes();
                if (v == null) {
                    continue;
                }
                for (int i = 0; i + 1 < v.size(); i += 2) {
                    line.add(new LatLng(v.get(i + 1), v.get(i))); // (위도, 경도) — vertexes는 경도,위도 순
                }
            }
        }
        return line;
    }

    @Override
    public String mode() {
        return "road";
    }
}
