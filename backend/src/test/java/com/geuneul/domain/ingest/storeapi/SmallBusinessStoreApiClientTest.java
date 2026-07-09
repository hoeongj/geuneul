package com.geuneul.domain.ingest.storeapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * 상권정보 오픈API 클라이언트 — ⚠️ 계약 미검증(StoreRecord 주석 참고, 활용신청 미승인).
 * 이 테스트는 "우리가 가정한 응답 계약을 우리 파서가 올바르게 소비하는가"만 고정한다.
 * 실 승인 후 실 호출로 이 픽스처 자체의 정확성을 재검증해야 한다.
 */
class SmallBusinessStoreApiClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SmallBusinessStoreApiClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SmallBusinessStoreApiClient("test-key", builder);
    }

    private static final String STORE_JSON = """
            {
              "response": {
                "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                "body": {
                  "items": [
                    {"bizesId": "S-1", "bizesNm": "그늘카페", "indsSclsNm": "커피전문점/카페/다방",
                     "indsSclsCd": "I56191", "rdnmAdr": "서울 동작구 상도로 1", "lnoAdr": null,
                     "lon": "126.9573", "lat": "37.4962"}
                  ],
                  "totalCount": "1", "numOfRows": "500", "pageNo": "1"
                }
              }
            }
            """;

    @Test
    @DisplayName("가정한 응답 계약을 파싱해 StorePage로 매핑한다")
    void parsesAssumedContract() {
        server.expect(requestTo(containsString("/B553077/api/open/sdsc2/storeListInRadius")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("cx", "126.9573"))
                .andExpect(queryParam("cy", "37.4962"))
                .andExpect(queryParam("radius", "500"))
                .andRespond(withSuccess(STORE_JSON, MediaType.APPLICATION_JSON));

        StorePage page = client.searchByRadius(37.4962, 126.9573, 500, null, 1, 500);

        assertThat(page.items()).hasSize(1);
        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items().get(0).bizesNm()).isEqualTo("그늘카페");
        assertThat(page.items().get(0).indsSclsNm()).isEqualTo("커피전문점/카페/다방");
    }

    @Test
    @DisplayName("HTTP 오류는 예외를 던지지 않고 빈 페이지로 처리한다")
    void errorResponseYieldsEmptyPage() {
        server.expect(requestTo(containsString("storeListInRadius")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)); // 실측 403(미승인) 시나리오와 동일 처리 경로

        assertThat(client.searchByRadius(37.5, 127.0, 500, null, 1, 500).items()).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 명확한 예외를 던진다")
    void throwsWhenNoKey() {
        SmallBusinessStoreApiClient noKeyClient = new SmallBusinessStoreApiClient("", RestClient.builder());
        assertThatThrownBy(() -> noKeyClient.searchByRadius(37.5, 127.0, 500, null, 1, 500))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATA_GO_KR_SERVICE_KEY");
    }
}
