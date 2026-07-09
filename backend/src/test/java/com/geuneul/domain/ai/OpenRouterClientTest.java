package com.geuneul.domain.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenRouter Chat Completions 요청/응답 계약을 실 직렬화기로 검증한다(TS-004 교훈 — 모킹은
 * "우리 로직"은 검증하되 "외부와의 실제 계약"을 가릴 수 있어, 요청 바디·헤더까지 MockRestServiceServer로
 * 못 박는다). 그늘의 domain/weather/WeatherClientTest와 동일한 패턴.
 */
class OpenRouterClientTest {

    private static final String BASE_URL = "https://openrouter.test/api/v1";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    @DisplayName("정상 응답: choices[0].message.content를 추출하고 앞뒤 공백을 제거한다")
    void extractsAndTrimsContent() {
        OpenRouterClient client = new OpenRouterClient("test-key", "qwen/qwen3-next-80b-a3b-instruct:free", BASE_URL, builder);
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen/qwen3-next-80b-a3b-instruct:free"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.max_tokens").value(150))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"  최근 제보 기준 시원해요  "}}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<String> result = client.complete("system-prompt", "user-prompt");

        assertThat(result).contains("최근 제보 기준 시원해요");
        server.verify();
    }

    @Test
    @DisplayName("빈 choices면 empty(모델이 답을 안 준 경우)")
    void emptyWhenNoChoices() {
        OpenRouterClient client = new OpenRouterClient("test-key", "any-model", BASE_URL, builder);
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete("s", "u")).isEmpty();
    }

    @Test
    @DisplayName("content가 빈 문자열이면 empty")
    void emptyWhenBlankContent() {
        OpenRouterClient client = new OpenRouterClient("test-key", "any-model", BASE_URL, builder);
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"   "}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete("s", "u")).isEmpty();
    }

    @Test
    @DisplayName("HTTP 오류(5xx)는 예외 없이 empty(graceful degradation)")
    void emptyOnHttpError() {
        OpenRouterClient client = new OpenRouterClient("test-key", "any-model", BASE_URL, builder);
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.complete("s", "u")).isEmpty();
    }

    @Test
    @DisplayName("HTTP 오류(429 rate limit)도 예외 없이 empty")
    void emptyOnRateLimit() {
        OpenRouterClient client = new OpenRouterClient("test-key", "any-model", BASE_URL, builder);
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client.complete("s", "u")).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON(파싱 실패)도 예외 없이 empty")
    void emptyOnMalformedJson() {
        OpenRouterClient client = new OpenRouterClient("test-key", "any-model", BASE_URL, builder);
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThat(client.complete("s", "u")).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 호출하지 않고 empty (앱은 AI 요약 없이도 동작)")
    void emptyWhenNoKey() {
        OpenRouterClient noKey = new OpenRouterClient("", "any-model", BASE_URL, RestClient.builder());
        assertThat(noKey.complete("s", "u")).isEmpty();
    }

    @Test
    @DisplayName("키가 blank(공백만)여도 호출하지 않고 empty")
    void emptyWhenBlankKey() {
        OpenRouterClient blankKey = new OpenRouterClient("   ", "any-model", BASE_URL, RestClient.builder());
        assertThat(blankKey.complete("s", "u")).isEmpty();
    }
}
