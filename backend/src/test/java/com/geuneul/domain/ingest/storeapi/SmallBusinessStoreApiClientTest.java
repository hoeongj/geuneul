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
 * 상권정보 오픈API 클라이언트 — <b>계약 검증 완료(2026-07-10, TS-026)</b>. 이 픽스처는 활용신청 승인 후
 * 실 호출로 확인한 실제 응답 형태(response 래퍼 없음, header/body 최상위, 좌표·페이지네이션 숫자)를 고정한다.
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

    // 실 응답 형태(2026-07-10 실측): response 래퍼 없이 header/body 최상위, lon/lat·totalCount 숫자.
    private static final String STORE_JSON = """
            {
              "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE",
                         "columns": ["상가업소번호"], "stdrYm": "202603"},
              "body": {
                "items": [
                  {"bizesId": "S-1", "bizesNm": "그늘카페", "indsSclsNm": "카페",
                   "indsSclsCd": "I21201", "rdnmAdr": "서울 동작구 상도로 1", "lnoAdr": null,
                   "lon": 126.9573, "lat": 37.4962}
                ],
                "totalCount": 1, "numOfRows": 1000, "pageNo": 1
              }
            }
            """;

    @Test
    @DisplayName("실측 응답 계약을 파싱해 StorePage로 매핑한다(숫자 좌표·래퍼 없음)")
    void parsesRealContract() {
        server.expect(requestTo(containsString("/B553077/api/open/sdsc2/storeListInRadius")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("cx", "126.9573"))
                .andExpect(queryParam("cy", "37.4962"))
                .andExpect(queryParam("radius", "500"))
                .andExpect(queryParam("indsSclsCd", "I21201"))
                .andRespond(withSuccess(STORE_JSON, MediaType.APPLICATION_JSON));

        StorePage page = client.searchByRadius(37.4962, 126.9573, 500, "I21201", 1, 1000);

        assertThat(page.items()).hasSize(1);
        assertThat(page.totalCount()).isEqualTo(1);
        StoreRecord r = page.items().get(0);
        assertThat(r.bizesNm()).isEqualTo("그늘카페");
        assertThat(r.indsSclsCd()).isEqualTo("I21201");
        assertThat(r.lon()).isEqualTo(126.9573);
        assertThat(r.lat()).isEqualTo(37.4962);
    }

    @Test
    @DisplayName("업종코드 없이도(null) 정상 파싱한다 — indsSclsCd 미부착 경로")
    void parsesWithoutCodeFilter() {
        server.expect(requestTo(containsString("storeListInRadius")))
                .andRespond(withSuccess(STORE_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.searchByRadius(37.4962, 126.9573, 500, null, 1, 1000).items()).hasSize(1);
    }

    @Test
    @DisplayName("HTTP 오류는 서비스키를 노출하지 않는 예외로 전파해 거짓 성공을 막는다")
    void errorResponseFailsClosedWithoutLeakingKey() {
        server.expect(requestTo(containsString("storeListInRadius")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)); // 승인 전 403(미승인)과 동일 처리 경로

        assertThatThrownBy(() -> client.searchByRadius(37.5, 127.0, 500, "I21201", 1, 1000))
                .isInstanceOf(StoreApiException.class)
                .hasMessageContaining("page=1")
                .hasMessageNotContaining("test-key");
    }

    @Test
    @DisplayName("외부 resultCode는 숫자 계약 밖 값을 로그·예외에 복제하지 않는다")
    void sanitizesUnexpectedResultCode() {
        server.expect(requestTo(containsString("storeListInRadius")))
                .andRespond(withSuccess(
                        "{\"header\":{\"resultCode\":\"test-key\",\"resultMsg\":\"body-secret\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchByRadius(37.5, 127.0, 500, "I21201", 1, 1000))
                .isInstanceOf(StoreApiException.class)
                .hasMessageContaining("resultCode=invalid")
                .hasMessageNotContaining("test-key")
                .hasMessageNotContaining("body-secret");
    }

    @Test
    @DisplayName("키가 없으면 명확한 예외를 던진다")
    void throwsWhenNoKey() {
        SmallBusinessStoreApiClient noKeyClient = new SmallBusinessStoreApiClient("", RestClient.builder());
        assertThatThrownBy(() -> noKeyClient.searchByRadius(37.5, 127.0, 500, "I21201", 1, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATA_GO_KR_SERVICE_KEY");
    }
}
