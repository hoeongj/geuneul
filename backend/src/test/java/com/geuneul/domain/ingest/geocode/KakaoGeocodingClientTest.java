package com.geuneul.domain.ingest.geocode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * 실제 카카오 응답 JSON을 RestClient 변환기로 역직렬화하는 경로를 검증한다 (TS-004 회귀 방지).
 * IngestionIdempotencyIT는 페이크 지오코더를 써서 이 경로가 사각지대였고, 그 결과 Boot 4(Jackson 3)에서
 * JsonNode 역직렬화가 전량 실패해 프로덕션 60k 적재가 0건이 됐다. 이 테스트가 그 클래스의 버그를 잡는다.
 */
class KakaoGeocodingClientTest {

    // 카카오 주소검색 실제 응답 형태(축약). x=경도, y=위도.
    private static final String KAKAO_JSON = """
            {
              "meta": {"total_count": 1},
              "documents": [{
                "address": {"x": "126.978652258309", "y": "37.566826004661"},
                "road_address": {"x": "126.998780325237", "y": "37.590489024417"}
              }]
            }
            """;

    // 4개 테스트 공통 셋업(test-key 클라이언트 + 모의 서버). throwsWhenNoKey만 자체 클라이언트 사용.
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KakaoGeocodingClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoGeocodingClient("test-key", builder);
    }

    @Test
    @DisplayName("정상 응답: 도로명(road_address) 좌표를 우선 사용해 lat/lng로 매핑한다")
    void deserializesRealKakaoResponse() {
        server.expect(requestTo(containsString("/v2/local/search/address.json")))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess(KAKAO_JSON, MediaType.APPLICATION_JSON));

        Optional<GeocodingClient.LatLng> result = client.geocode("서울특별시 종로구 성균관로 91");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.590489024417);  // road_address.y
        assertThat(result.get().lng()).isEqualTo(126.998780325237); // road_address.x
        server.verify();
    }

    @Test
    @DisplayName("도로명이 없으면 지번(address) 좌표로 폴백한다")
    void fallsBackToJibunWhenNoRoadAddress() {
        server.expect(requestTo(containsString("/v2/local/search/address.json")))
                .andRespond(withSuccess("""
                        {"documents":[{"address":{"x":"127.1","y":"37.2"},"road_address":null}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<GeocodingClient.LatLng> result = client.geocode("어떤 지번 주소");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.2);
        assertThat(result.get().lng()).isEqualTo(127.1);
    }

    @Test
    @DisplayName("결과 0건이면 empty")
    void emptyWhenNoDocuments() {
        server.expect(requestTo(containsString("/v2/local/search/address.json")))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.geocode("없는 주소")).isEmpty();
    }

    @Test
    @DisplayName("HTTP 오류(4xx)는 예외를 던지지 않고 empty로 처리한다")
    void serverErrorReturnsEmpty() {
        server.expect(requestTo(containsString("/v2/local/search/address.json")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

        assertThat(client.geocode("주소")).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 명확한 예외를 던진다")
    void throwsWhenNoKey() {
        KakaoGeocodingClient client = new KakaoGeocodingClient("", RestClient.builder());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.geocode("주소"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_REST_API_KEY");
    }
}
