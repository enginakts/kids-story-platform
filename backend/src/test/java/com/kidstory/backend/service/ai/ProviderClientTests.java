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

/** Featherless (Trendyol) ve Gemini adaptörlerinin istek/yanıt sözleşmesi, sahte yerel HTTP sunucusuyla. */
class ProviderClientTests {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> headers = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void featherlessSendsPlainChatRequestAndStripsThinking() throws IOException {
        start("/v1/chat/completions", 200, Map.of(), """
                {"choices":[{"finish_reason":"stop","message":{"content":"<think>plan</think>\\nBaşlık\\n\\nMasal metni."}}]}""");
        String content = featherless().complete(request(null), config("Trendyol/Trendyol-LLM-8B-T1"));

        assertThat(content).isEqualTo("Başlık\n\nMasal metni.");
        assertThat(headers.get().get("Authorization")).isEqualTo("Bearer f-key");
        assertThat(body.get()).contains("\"model\":\"Trendyol/Trendyol-LLM-8B-T1\"").contains("\"max_tokens\":2048")
                .contains("\"role\":\"system\"").doesNotContain("response_format");
    }

    @Test
    void featherlessCapacityErrorIsRetryableWithoutBody() throws IOException {
        start("/v1/chat/completions", 503, Map.of(), "{\"error\":\"No valid Executor\"}");
        assertThatThrownBy(() -> featherless().complete(request(null), config("m")))
                .hasMessage("Featherless isteği başarısız: HTTP 503");
    }

    @Test
    void featherlessRateLimitBecomesRateLimited() throws IOException {
        start("/v1/chat/completions", 429, Map.of("retry-after", "9"), "{}");
        assertThatThrownBy(() -> featherless().complete(request(null), config("m")))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(9));
    }

    @Test
    void geminiUsesInteractionsApiWithSchemaAndNoStorage() throws IOException {
        start("/v1beta/interactions", 200, Map.of(), """
                {"id":"v1_x","object":"interaction","status":"completed","steps":[
                 {"type":"model_output","content":[{"type":"text","text":"{\\"title\\":\\"T\\","},{"type":"text","text":"\\"scenes\\":[]}"}]}],
                 "usage":{"total_tokens":10}}""");
        String content = gemini().complete(request(Map.of("type", "object")), config("gemini-3.6-flash"));

        assertThat(content).isEqualTo("{\"title\":\"T\",\"scenes\":[]}");
        assertThat(headers.get().get("X-goog-api-key")).isEqualTo("g-key");
        assertThat(headers.get().get("Api-revision")).isEqualTo("2026-05-20");
        assertThat(body.get()).contains("\"model\":\"gemini-3.6-flash\"").contains("\"system_instruction\":\"Sistem kuralı\"")
                .contains("\"input\":\"Kullanıcı isteği\"").contains("\"mime_type\":\"application/json\"")
                .contains("\"thinking_level\":\"low\"").contains("\"store\":false");
    }

    @Test
    void geminiIncompleteAnswerIsRejected() throws IOException {
        start("/v1beta/interactions", 200, Map.of(), "{\"status\":\"incomplete\",\"steps\":[]}");
        assertThatThrownBy(() -> gemini().complete(request(null), config("g"))).hasMessageContaining("tamamlanmadı");
    }

    @Test
    void geminiRateLimitBecomesRateLimited() throws IOException {
        start("/v1beta/interactions", 429, Map.of(), "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}");
        assertThatThrownBy(() -> gemini().complete(request(null), config("g")))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(30));
    }

    private void start(String path, int status, Map<String, String> responseHeaders, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> h = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> h.put(k, v.getFirst()));
            headers.set(h);
            responseHeaders.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private FeatherlessLlmClient featherless() {
        return new FeatherlessLlmClient(RestClient.builder(), base() + "/v1", "f-key");
    }

    private GeminiLlmClient gemini() {
        return new GeminiLlmClient(RestClient.builder(), base() + "/v1beta", "g-key", "2026-05-20");
    }

    private static LlmRequest request(Map<String, Object> schema) {
        return new LlmRequest("Sistem kuralı", "Kullanıcı isteği", schema == null ? null : "story", schema);
    }

    private static AiProviderConfig config(String model) {
        AiProviderConfig config = new AiProviderConfig();
        config.setProviderName("x");
        config.setActiveModelName(model);
        config.setTimeoutMs(5000);
        return config;
    }
}
