package com.knowledgeforge.knowledgeforge.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Sends ONE OpenAI-style chat-completions request (Groq today) and returns the text plus token usage.
// Never logs the API key, prompts or the answer. Every provider problem becomes a 503 JSON message.
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final String BUSY = "AI is busy right now, try again in a minute";

    public record Result(String text, int promptTokens, int completionTokens, int totalTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Completion(List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {}

    // Groq may also send message.reasoning; we do not declare it, so it is dropped.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
                 @JsonProperty("completion_tokens") Integer completionTokens,
                 @JsonProperty("total_tokens") Integer totalTokens) {}

    private final RestClient http;
    private final String model;
    private final boolean configured;

    @Autowired
    public AiClient(@Value("${ai.base-url:}") String baseUrl,
                    @Value("${ai.model:}") String model,
                    @Value("${ai.api-key:}") String apiKey) {
        this(baseUrl, model, apiKey, RestClient.builder().requestFactory(timeoutFactory()));
    }

    // Tests pass a builder that already has a mock server bound to it.
    AiClient(String baseUrl, String model, String apiKey, RestClient.Builder builder) {
        this.configured = !baseUrl.isBlank() && !model.isBlank() && !apiKey.isBlank();
        this.model = model;
        this.http = builder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    private static JdkClientHttpRequestFactory timeoutFactory() {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory();
        f.setReadTimeout(Duration.ofSeconds(20));
        return f;
    }

    private static int n(Integer i) {
        return i == null ? 0 : i;
    }

    public boolean isConfigured() {
        return configured;
    }

    public Result chat(String system, String user, int maxCompletionTokens) {
        if (!configured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI is not configured on this server");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "max_completion_tokens", maxCompletionTokens,
                "temperature", 0.2,
                "reasoning_effort", "low",
                "include_reasoning", false); // keeps the hidden reasoning out of the response

        Completion c;
        try {
            c = http.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Completion.class);
        } catch (Exception e) {
            // 429, 5xx, timeout, bad JSON: log only the exception type, never the message (could echo input).
            log.warn("AI request failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, BUSY);
        }

        Choice choice = c == null || c.choices() == null || c.choices().isEmpty() ? null : c.choices().get(0);
        String text = choice == null || choice.message() == null ? null : choice.message().content();
        if (text == null || text.isBlank() || "length".equals(choice.finishReason())) {
            // Out of tokens (reasoning ate the budget) or empty answer.
            log.warn("AI answer unusable, finish_reason={}", choice == null ? "none" : choice.finishReason());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, BUSY);
        }
        Usage u = c.usage() == null ? new Usage(0, 0, 0) : c.usage();
        return new Result(text.trim(), n(u.promptTokens()), n(u.completionTokens()), n(u.totalTokens()));
    }
}
