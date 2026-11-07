package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Groq'u taklit eden yerel HTTP sunucusuyla adaptörün istek ve yanıt sözleşmesi. */
class GroqLlmClientTests {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsStrictJsonSchemaRequestAndReturnsContent() throws IOException {
        start(200, Map.of(), """
                {"id":"x","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"{\\"title\\":\\"T\\",\\"scenes\\":[]}"}}],"usage":{}}""");
        String content = client().complete(request(), config());

        assertThat(content).isEqualTo("{\"title\":\"T\",\"scenes\":[]}");
        assertThat(auth.get()).isEqualTo("Bearer test-key");
        assertThat(body.get()).contains("\"model\":\"openai/gpt-oss-120b\"")
                .contains("\"type\":\"json_schema\"").contains("\"strict\":true")
                .contains("\"reasoning_effort\":\"low\"").contains("\"include_reasoning\":false")
                .contains("\"role\":\"system\"").contains("Sistem kuralı");
    }

    @Test
    void rateLimitBecomesRetryableWithRetryAfter() throws IOException {
        start(429, Map.of("retry-after", "7"), "{\"error\":{\"message\":\"rate limited\"}}");
        assertThatThrownBy(() -> client().complete(request(), config()))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(7));
    }

    @Test
    void serverErrorDoesNotLeakBody() throws IOException {
        start(500, Map.of(), "{\"error\":\"secret details\"}");
        assertThatThrownBy(() -> client().complete(request(), config()))
                .hasMessage("Groq isteği başarısız: HTTP 500");
    }

    @Test
    void truncatedAnswerIsRejected() throws IOException {
        start(200, Map.of(), """
                {"choices":[{"finish_reason":"length","message":{"content":"{\\"title\\""}}]}""");
        assertThatThrownBy(() -> client().complete(request(), config())).hasMessageContaining("token sınırında");
    }

    private void start(int status, Map<String, String> headers, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private GroqLlmClient client() {
        return new GroqLlmClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "test-key");
    }

    private static LlmRequest request() {
        return new LlmRequest("Sistem kuralı", "Kullanıcı isteği", "story",
                Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));
    }

    private static AiProviderConfig config() {
        AiProviderConfig config = new AiProviderConfig();
        config.setProviderName("groq");
        config.setActiveModelName("openai/gpt-oss-120b");
        config.setTimeoutMs(5000);
        return config;
    }
}
