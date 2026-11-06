package com.knowledgeforge.knowledgeforge.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiClientTest {

    private static final String URL = "https://ai.test/v1";

    private MockRestServiceServer server;

    private AiClient client(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AiClient(URL, "test-model", apiKey, builder);
    }

    private static ResponseStatusException thrown(AiClient c) {
        return assertThrows(ResponseStatusException.class, () -> c.chat("sys", "user", 100));
    }

    @Test
    void sendsExpectedRequestAndReadsAnswerAndUsage() {
        AiClient c = client("secret");
        server.expect(requestTo(URL + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_completion_tokens").value(100))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.include_reasoning").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("user"))
                .andRespond(withSuccess("""
                        {"choices":[{"finish_reason":"stop","message":{"content":" Hello ","reasoning":"secret thoughts"}}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,"queue_time":0.1}}
                        """, MediaType.APPLICATION_JSON));

        AiClient.Result r = c.chat("sys", "user", 100);

        assertEquals("Hello", r.text());
        assertEquals(15, r.totalTokens());
        assertEquals(10, r.promptTokens());
        assertEquals(5, r.completionTokens());
        server.verify();
    }

    @Test
    void providerRateLimitBecomes503() {
        AiClient c = client("k");
        server.expect(requestTo(URL + "/chat/completions")).andRespond(withTooManyRequests());
        ResponseStatusException e = thrown(c);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals("AI is busy right now, try again in a minute", e.getReason());
    }

    @Test
    void providerServerErrorBecomes503() {
        AiClient c = client("k");
        server.expect(requestTo(URL + "/chat/completions")).andRespond(withServerError());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, thrown(c).getStatusCode());
    }

    @Test
    void cutOffAnswerBecomes503() {
        AiClient c = client("k");
        server.expect(requestTo(URL + "/chat/completions")).andRespond(withSuccess("""
                {"choices":[{"finish_reason":"length","message":{"content":""}}],"usage":{"total_tokens":1000}}
                """, MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, thrown(c).getStatusCode());
    }

    @Test
    void missingKeyBecomes503NotConfigured() {
        AiClient c = client("");
        assertFalse(c.isConfigured());
        ResponseStatusException e = thrown(c);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals("AI is not configured on this server", e.getReason());
    }
}
