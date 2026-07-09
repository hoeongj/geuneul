package com.geuneul.domain.weather;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 기상청 초단기실황 응답 JSON을 RestClient 변환기로 역직렬화하는 경로를 검증한다.
 * (KakaoGeocodingClientTest와 같은 취지 — Boot 4/Jackson 3에서 record 매핑이 실제로 되는지 못 박는다.)
 */
class WeatherClientTest {

    // 기상청 getUltraSrtNcst 실제 응답 형태(축약). obsrValue=관측값. RN1 "강수없음"·PTY 코드 포함.
    private static final String NCST_JSON = """
            {
              "response": {
                "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                "body": {
                  "dataType": "JSON",
                  "items": {"item": [
                    {"baseDate":"20260709","baseTime":"1400","category":"T1H","nx":60,"ny":127,"obsrValue":"29.3"},
                    {"baseDate":"20260709","baseTime":"1400","category":"REH","nx":60,"ny":127,"obsrValue":"65"},
                    {"baseDate":"20260709","baseTime":"1400","category":"RN1","nx":60,"ny":127,"obsrValue":"강수없음"},
                    {"baseDate":"20260709","baseTime":"1400","category":"PTY","nx":60,"ny":127,"obsrValue":"0"}
                  ]}
                }
              }
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private WeatherClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WeatherClient("test-key", builder);
    }

    @Test
    @DisplayName("정상 응답: T1H/REH/RN1/PTY를 Weather로 매핑한다")
    void deserializesNcst() {
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andRespond(withSuccess(NCST_JSON, MediaType.APPLICATION_JSON));

        Optional<Weather> result = client.fetchNowcast(60, 127, "20260709", "1400");

        assertThat(result).isPresent();
        Weather w = result.get();
        assertThat(w.temperatureC()).isEqualTo(29.3);
        assertThat(w.humidityPct()).isEqualTo(65);
        assertThat(w.rain1hMm()).isEqualTo(0.0);                 // "강수없음" → 0.0
        assertThat(w.precipitation()).isEqualTo(PrecipitationType.NONE);
        assertThat(w.observedAt()).isEqualTo("202607091400");
        server.verify();
    }

    @Test
    @DisplayName("비 오는 중: PTY=1(비), RN1 수치 파싱")
    void parsesRain() {
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                          {"category":"T1H","obsrValue":"24.0"},
                          {"category":"PTY","obsrValue":"1"},
                          {"category":"RN1","obsrValue":"2.5mm"}
                        ]}}}}
                        """, MediaType.APPLICATION_JSON));

        Weather w = client.fetchNowcast(60, 127, "20260709", "1400").orElseThrow();
        assertThat(w.precipitation()).isEqualTo(PrecipitationType.RAIN);
        assertThat(w.precipitation().isPrecipitating()).isTrue();
        assertThat(w.rain1hMm()).isEqualTo(2.5);
    }

    @Test
    @DisplayName("기상청 오류 코드(resultCode!=00)면 empty")
    void emptyOnErrorCode() {
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{"items":{"item":[]}}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchNowcast(60, 127, "20260709", "1400")).isEmpty();
    }

    @Test
    @DisplayName("HTTP 오류(5xx)는 예외 없이 empty")
    void emptyOnHttpError() {
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.fetchNowcast(60, 127, "20260709", "1400")).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 호출하지 않고 empty (앱은 날씨 없이 동작)")
    void emptyWhenNoKey() {
        WeatherClient noKey = new WeatherClient("", RestClient.builder());
        assertThat(noKey.fetchNowcast(60, 127, "20260709", "1400")).isEmpty();
    }
}
