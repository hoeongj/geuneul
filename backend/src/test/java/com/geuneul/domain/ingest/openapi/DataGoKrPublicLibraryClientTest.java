package com.geuneul.domain.ingest.openapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * 전국도서관표준데이터 오픈API 클라이언트 — 2026-07-09 실측 응답 구조(response.header/body,
 * items 배열, 결측 컬럼은 빈 문자열)를 그대로 픽스처로 검증한다(TS-004류 사각지대 방지: 페이크가
 * 아니라 실제 파싱 경로를 태운다).
 */
class DataGoKrPublicLibraryClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private DataGoKrPublicLibraryClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DataGoKrPublicLibraryClient("test-key", builder);
    }

    private static String fixture(String name) {
        try (InputStream in = DataGoKrPublicLibraryClientTest.class
                .getResourceAsStream("/fixtures/openapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("정상 응답: items를 파싱하고 좌표/열람좌석수 결측(빈 문자열)도 그대로 보존한다")
    void parsesRealShapedResponse() {
        server.expect(requestTo(containsString("/openapi/tn_pubr_public_lbrry_api")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("pageNo", "1"))
                .andExpect(queryParam("numOfRows", "500"))
                .andRespond(withSuccess(fixture("library_page1.json"), MediaType.APPLICATION_JSON));

        LibraryPage page = client.fetchPage(1, 500);

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.items().get(0).lbrryNm()).isEqualTo("숭실대학교 중앙도서관");
        assertThat(page.items().get(0).seatCo()).isEqualTo("800");
        assertThat(page.items().get(1).latitude()).isEmpty(); // 좌표 결측 — 지오코딩 대상
        assertThat(page.items().get(1).seatCo()).isEmpty();   // 열람좌석수 결측 — study_ok 백필 제외
    }

    @Test
    @DisplayName("NODATA_ERROR(resultCode=03, body 없음): 마지막 페이지 다음 — 빈 페이지로 처리")
    void nodataErrorYieldsEmptyPage() {
        server.expect(requestTo(containsString("/openapi/tn_pubr_public_lbrry_api")))
                .andRespond(withSuccess(
                        "{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NODATA_ERROR\"}}}",
                        MediaType.APPLICATION_JSON));

        LibraryPage page = client.fetchPage(1000, 500);

        assertThat(page.items()).isEmpty();
    }

    @Test
    @DisplayName("HTTP 오류는 예외를 던지지 않고 빈 페이지로 처리한다")
    void errorResponseYieldsEmptyPage() {
        server.expect(requestTo(containsString("/openapi/tn_pubr_public_lbrry_api")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.fetchPage(1, 500).items()).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 명확한 예외를 던진다")
    void throwsWhenNoKey() {
        DataGoKrPublicLibraryClient noKeyClient = new DataGoKrPublicLibraryClient("", RestClient.builder());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> noKeyClient.fetchPage(1, 500))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATA_GO_KR_SERVICE_KEY");
    }
}
