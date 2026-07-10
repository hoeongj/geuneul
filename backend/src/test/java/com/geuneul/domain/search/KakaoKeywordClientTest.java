package com.geuneul.domain.search;

import com.geuneul.domain.search.dto.PlaceSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 키워드 검색 응답(실제 형태)을 RestClient 변환기로 역직렬화하는 경로를 검증한다(N5, TS-004 회귀 방지 —
 * Boot 4=Jackson 3에서 record 매핑이 정상인지, x=경도·y=위도 축을 안 뒤집는지). 실호출 계약은 TS-026으로 별도 검증.
 */
class KakaoKeywordClientTest {

    // 카카오 키워드 검색 실제 응답 형태(축약). x=경도, y=위도(문자열).
    private static final String KAKAO_JSON = """
            {
              "meta": {"total_count": 2, "is_end": true},
              "documents": [
                {
                  "place_name": "강남역 2호선",
                  "address_name": "서울 강남구 역삼동 858",
                  "road_address_name": "서울 강남구 강남대로 지하 396",
                  "x": "127.02800140627488",
                  "y": "37.49808633653005",
                  "category_group_name": "지하철역"
                },
                {
                  "place_name": "좌표 없는 문서",
                  "address_name": "주소만",
                  "road_address_name": null,
                  "x": null,
                  "y": null,
                  "category_group_name": ""
                }
              ]
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KakaoKeywordClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoKeywordClient("test-key", builder);
    }

    @Test
    @DisplayName("정상 응답: place_name/주소/좌표 매핑(x=경도,y=위도) + 좌표 없는 문서는 버린다")
    void deserializesRealKakaoResponse() {
        server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess(KAKAO_JSON, MediaType.APPLICATION_JSON));

        List<PlaceSearchResult> results = client.search("강남역", null, null, 10);

        assertThat(results).hasSize(1); // 좌표 null 문서는 필터됨
        PlaceSearchResult r = results.get(0);
        assertThat(r.name()).isEqualTo("강남역 2호선");
        assertThat(r.address()).isEqualTo("서울 강남구 역삼동 858");
        assertThat(r.roadAddress()).isEqualTo("서울 강남구 강남대로 지하 396");
        assertThat(r.lat()).isEqualTo(37.49808633653005); // y
        assertThat(r.lng()).isEqualTo(127.02800140627488); // x
        assertThat(r.category()).isEqualTo("지하철역");
        server.verify();
    }

    @Test
    @DisplayName("좌표(lat/lng)를 주면 sort=distance + x/y 쿼리로 거리순 정렬을 요청한다")
    void locationBiasAddsDistanceSort() {
        server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
                .andExpect(queryParam("sort", "distance"))
                .andExpect(queryParam("x", "127.0"))
                .andExpect(queryParam("y", "37.5"))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        client.search("성균관대", 37.5, 127.0, 10);
        server.verify();
    }

    @Test
    @DisplayName("좌표가 없으면 sort 파라미터를 넣지 않는다(카카오가 sort=distance엔 x/y를 요구)")
    void noBiasOmitsSort() {
        server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
                .andExpect(queryParam("size", "10"))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        List<PlaceSearchResult> results = client.search("강남역", null, null, 10);
        assertThat(results).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("size는 카카오 상한(15)으로 클램프된다")
    void sizeClampedToMax() {
        server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
                .andExpect(queryParam("size", "15"))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        client.search("강남역", null, null, 999);
        server.verify();
    }

    @Test
    @DisplayName("서버 오류는 500으로 던지지 않고 빈 리스트로 흘린다(graceful)")
    void serverErrorReturnsEmpty() {
        server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
                .andRespond(withServerError());

        assertThat(client.search("강남역", null, null, 10)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("REST 키가 없으면 카카오를 호출하지 않고 빈 리스트를 돌려준다")
    void noKeyReturnsEmptyWithoutCall() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer s = MockRestServiceServer.bindTo(b).build();
        KakaoKeywordClient noKey = new KakaoKeywordClient("", b);

        assertThat(noKey.search("강남역", null, null, 10)).isEmpty();
        s.verify(); // 아무 요청도 없어야 통과
    }
}
