package com.geuneul.domain.route;

import com.geuneul.domain.route.dto.LatLng;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * 카카오모빌리티 waypoints 길찾기 실제 응답 JSON을 RestClient 변환기로 역직렬화하는 경로를 검증한다(F3, TS-004/TS-026).
 * 실호출 계약(엔드포인트·키 인가)은 2026-07-11에 검증했고(WORKLOG), 여기선 파싱/폴백 로직을 DB·네트워크 없이 굳힌다.
 * vertexes는 [경도,위도,…] 평탄 배열 → LatLng(위도,경도)로 뒤집혀야 한다(순서 뒤집으면 지도에서 좌표가 엉킴).
 */
class KakaoDirectionsProviderTest {

    // 실제 응답 축약 — 2개 구간(section), 각 도로(road)의 vertexes=[lng,lat,lng,lat].
    private static final String KAKAO_JSON = """
            {
              "routes": [{
                "result_code": 0,
                "result_msg": "길찾기 성공",
                "sections": [
                  {"roads": [{"vertexes": [126.9769, 37.5759, 126.9780, 37.5762]}]},
                  {"roads": [{"vertexes": [126.9857, 37.5636, 126.9707, 37.5547]}]}
                ]
              }]
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KakaoDirectionsProvider provider;

    private static final List<LatLng> WAYPOINTS = List.of(
            new LatLng(37.5759, 126.9769),  // 출발
            new LatLng(37.5636, 126.9857),  // 경유 화장실
            new LatLng(37.5547, 126.9707)); // 도착

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new KakaoDirectionsProvider("test-key", builder);
    }

    @Test
    @DisplayName("정상 응답: 모든 section·road의 vertexes를 순서대로 이어 (위도,경도) 폴리라인으로 매핑한다")
    void deserializesRoadPolyline() {
        server.expect(requestTo(containsString("/v1/waypoints/directions")))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess(KAKAO_JSON, MediaType.APPLICATION_JSON));

        Optional<List<LatLng>> line = provider.polyline(WAYPOINTS);

        assertThat(line).isPresent();
        assertThat(line.get()).hasSize(4); // 2 section × 2점
        assertThat(line.get().get(0).lat()).isEqualTo(37.5759); // vertexes[1]
        assertThat(line.get().get(0).lng()).isEqualTo(126.9769); // vertexes[0]
        assertThat(line.get().get(3).lat()).isEqualTo(37.5547);
        assertThat(provider.mode()).isEqualTo("road");
        server.verify();
    }

    @Test
    @DisplayName("result_code!=0(길찾기 실패)이면 empty → 호출부 직선 폴백")
    void emptyOnNonZeroResultCode() {
        server.expect(requestTo(containsString("/v1/waypoints/directions")))
                .andRespond(withSuccess("""
                        {"routes":[{"result_code":104,"result_msg":"출발지와 도착지가 5m 이내","sections":null}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.polyline(WAYPOINTS)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("HTTP 오류(쿼터/네트워크)면 예외를 삼키고 empty → 직선 폴백(graceful)")
    void emptyOnHttpError() {
        server.expect(requestTo(containsString("/v1/waypoints/directions")))
                .andRespond(withServerError());

        assertThat(provider.polyline(WAYPOINTS)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 HTTP 호출 없이 즉시 empty(로컬/CI에서 직선 폴백)")
    void emptyWithoutKeyAndNoHttpCall() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer strict = MockRestServiceServer.bindTo(b).build(); // 어떤 요청도 기대 안 함
        KakaoDirectionsProvider noKey = new KakaoDirectionsProvider("", b);

        assertThat(noKey.polyline(WAYPOINTS)).isEmpty();
        strict.verify(); // 호출이 있었으면 실패
    }

    @Test
    @DisplayName("경유지 2점 미만이면 호출 없이 empty")
    void emptyWhenTooFewPoints() {
        assertThat(provider.polyline(List.of(new LatLng(37.5, 126.9))).isEmpty()).isTrue();
    }
}
