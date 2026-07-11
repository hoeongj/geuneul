package com.geuneul.domain.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 프로바이더 중립 OpenAI 호환 Chat Completions 클라이언트 — 장소 AI 한줄 요약 전용(P3, 곁다리).
 *
 * <p><b>Anthropic(Claude API) 대신 OpenAI 호환 REST 프로바이더를 쓰는 이유</b>(docs/SPEC.md §8 "AI는 Claude API
 * 기본" 이탈, §0-B 의사결정 프로토콜에 따라 기록): 이 환경에 Anthropic API 키가 없다. 사용자가 보유한
 * 멀티프로바이더 무료/저가 폴백 키체인(base-url·key·model 전부 설정값이라 프로바이더 교체가 config만으로
 * 가능) 중 현재는 Mistral을 프라이머리로 쓴다. 근거는 WORKLOG·ADR-0010에 상세 기록.
 *
 * <p>이 클래스는 domain/weather의 WeatherClient 회복탄력 패턴을 그대로 따른다:
 * <ul>
 *   <li>키 미설정이면 호출 자체를 생략(Optional.empty)</li>
 *   <li>모든 예외(타임아웃 포함)를 여기서 삼키고 empty로 반환 — 절대 500을 내지 않는다</li>
 *   <li>타임아웃은 공통 RestClient.Builder 설정(연결 3s·읽기 10s)을 따른다</li>
 * </ul>
 */
@Component
public class ChatCompletionClient {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionClient.class);
    private static final int MAX_TOKENS = 150;
    private static final double TEMPERATURE = 0.3;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final boolean keyPresent;

    @Autowired
    public ChatCompletionClient(
            @Value("${ai.summary.api-key:}") String apiKey,
            @Value("${ai.summary.model:mistral-small-latest}") String model,
            @Value("${ai.summary.base-url:https://api.mistral.ai/v1}") String baseUrl,
            RestClient.Builder builder) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.keyPresent = !this.apiKey.isBlank();
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * 한국어 한 문장 요약을 요청한다. 키 미설정·네트워크 오류·타임아웃·빈 응답 등 **모든 실패는 empty**로
     * 흡수한다(호출부는 요약 없이도 정상 응답해야 한다 — graceful degradation).
     */
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (!keyPresent) {
            log.warn("[ai] AI_SUMMARY_API_KEY 미설정 — AI 요약을 건너뜁니다(규칙 D: .env/SSM으로만).");
            return Optional.empty();
        }
        try {
            ChatRequest body = new ChatRequest(
                    model,
                    List.of(
                            new ChatRequest.Message("system", systemPrompt),
                            new ChatRequest.Message("user", userPrompt)),
                    MAX_TOKENS,
                    TEMPERATURE);

            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("chat completion status=" + res.getStatusCode());
                    })
                    .body(ChatResponse.class);

            String text = extractText(response);
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(text.trim());
        } catch (Exception e) {
            log.warn("[ai] AI 요약 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        ChatResponse.Choice first = response.choices().get(0);
        if (first == null || first.message() == null) {
            return null;
        }
        return first.message().content();
    }

    /** OpenAI 호환 Chat Completions 요청 바디(대부분의 OpenAI 호환 프로바이더가 그대로 수용). */
    record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature) {
        record Message(String role, String content) {
        }
    }

    /** 응답은 필요 필드만 역직렬화(타입 바인드 record — TS-004와 동일 사유로 JsonNode 대신 record). */
    record ChatResponse(List<Choice> choices) {
        record Choice(Message message) {
        }

        record Message(String role, String content) {
        }
    }
}
