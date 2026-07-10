package com.geuneul.domain.ingest.safetydata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * safetydata 무더위쉼터 클라이언트 — <b>계약 검증 완료(2026-07-10, TS-027)</b>. 픽스처는 실호출로 확인한
 * 실제 응답(response 래퍼 없음, header/body/totalCount 최상위, body가 레코드 배열 그 자체, 좌표 숫자)을 고정한다.
 */
class SafetyDataShelterClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SafetyDataShelterClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SafetyDataShelterClient("test-key", builder);
    }

    // 실 응답 형태(2026-07-10 실측): 최상위 header/totalCount/body[], body는 items 없이 배열, LA/LO 숫자.
    private static final String SHELTER_JSON = """
            {
              "header": {"resultMsg": "NORMAL SERVICE", "resultCode": "00", "errorMsg": null},
              "numOfRows": 2, "pageNo": 1, "totalCount": 60297,
              "body": [
                {"RSTR_FCLTY_NO": 281104, "RSTR_NM": "연안동행정복지센터",
                 "RN_DTL_ADRES": "인천광역시 제물포구 축항대로86번길 44", "DTL_ADRES": "58-70",
                 "LA": 37.45317, "LO": 126.604502, "COLR_HOLD_ARCNDTN": 4, "XCORD": null, "YCORD": null}
              ]
            }
            """;

    @Test
    @DisplayName("실측 응답 계약을 파싱해 ShelterPage로 매핑한다(래퍼 없음·숫자 좌표·body 배열)")
    void parsesRealContract() {
        server.expect(requestTo(containsString("/V2/api/DSSP-IF-10942")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("pageNo", "1"))
                .andExpect(queryParam("numOfRows", "1000"))
                .andRespond(withSuccess(SHELTER_JSON, MediaType.APPLICATION_JSON));

        ShelterPage page = client.fetchPage(1, 1000);

        assertThat(page.totalCount()).isEqualTo(60297);
        assertThat(page.items()).hasSize(1);
        ShelterRecord r = page.items().get(0);
        assertThat(r.rstrFcltyNo()).isEqualTo(281104L);
        assertThat(r.rstrNm()).isEqualTo("연안동행정복지센터");
        assertThat(r.la()).isEqualTo(37.45317);
        assertThat(r.lo()).isEqualTo(126.604502);
        assertThat(r.colrHoldArcndtn()).isEqualTo(4);
    }

    @Test
    @DisplayName("HTTP 오류·비정상 resultCode는 예외 없이 빈 페이지로 처리한다")
    void errorResponseYieldsEmptyPage() {
        server.expect(requestTo(containsString("DSSP-IF-10942")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.fetchPage(1, 1000).items()).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 명확한 예외를 던진다(safetydata 전용 키 안내)")
    void throwsWhenNoKey() {
        SafetyDataShelterClient noKeyClient = new SafetyDataShelterClient("", RestClient.builder());
        assertThatThrownBy(() -> noKeyClient.fetchPage(1, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAFETYDATA_SERVICE_KEY");
    }
}
