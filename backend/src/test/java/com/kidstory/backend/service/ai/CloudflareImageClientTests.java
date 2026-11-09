package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cloudflare Workers AI (FLUX) adaptörünün istek/yanıt sözleşmesi, sahte yerel HTTP sunucusuyla. */
class CloudflareImageClientTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> headers = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void flux2SendsMultipartWithSizeSeedAndReferenceImages() throws IOException {
        start(200, Map.of(), ok(PNG));
        ImageClient.ImageResult result = client().generate(new ImageClient.ImageRequest("a bunny in a meadow", 1024, 768, 42L,
                List.of("REF0".getBytes(StandardCharsets.UTF_8), "REF1".getBytes(StandardCharsets.UTF_8))), config("@cf/black-forest-labs/flux-2-klein-4b"));

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.image()).isEqualTo(PNG);
        assertThat(path.get()).isEqualTo("/client/v4/accounts/acc-1/ai/run/@cf/black-forest-labs/flux-2-klein-4b");
        assertThat(headers.get().get("Authorization")).isEqualTo("Bearer cf-token");
        assertThat(headers.get().get("Content-type")).startsWith("multipart/form-data");
        assertThat(body.get()).contains("name=\"prompt\"").contains("a bunny in a meadow")
                .contains("name=\"width\"").contains("1024").contains("name=\"height\"").contains("768")
                .contains("name=\"seed\"").contains("42")
                .contains("name=\"input_image_0\"; filename=\"reference-0.jpg\"").contains("REF0")
                .contains("name=\"input_image_1\"").contains("REF1")
                .doesNotContain("name=\"steps\"");
    }

    @Test
    void flux1SendsJsonPromptOnly() throws IOException {
        start(200, Map.of(), ok(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}));
        ImageClient.ImageResult result = client().generate(new ImageClient.ImageRequest("a bunny", 1024, 768, 1L, List.of(new byte[]{1})),
                config("@cf/black-forest-labs/flux-1-schnell"));
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(headers.get().get("Content-type")).startsWith("application/json");
        assertThat(body.get()).contains("\"prompt\":\"a bunny\"").contains("\"steps\":4").doesNotContain("width").doesNotContain("input_image");
    }

    @Test
    void quotaOrRateLimitIsRetryable() throws IOException {
        start(429, Map.of("retry-after", "60"), "{\"success\":false,\"errors\":[{\"code\":3036}]}");
        assertThatThrownBy(() -> client().generate(request(), config("@cf/black-forest-labs/flux-2-klein-4b")))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));
    }

    @Test
    void errorsAndEmptyResultsAreRejectedWithoutBody() throws IOException {
        start(400, Map.of(), "{\"success\":false,\"errors\":[{\"message\":\"secret prompt\"}]}");
        assertThatThrownBy(() -> client().generate(request(), config("@cf/black-forest-labs/flux-2-klein-4b")))
                .hasMessage("Cloudflare görsel isteği başarısız: HTTP 400");
        server.stop(0);
        start(200, Map.of(), "{\"success\":false,\"result\":null}");
        assertThatThrownBy(() -> client().generate(request(), config("@cf/black-forest-labs/flux-2-klein-4b")))
                .hasMessageContaining("görsel yok");
    }

    private static String ok(byte[] image) {
        return "{\"result\":{\"image\":\"" + Base64.getEncoder().encodeToString(image) + "\"},\"success\":true,\"errors\":[],\"messages\":[]}";
    }

    private static ImageClient.ImageRequest request() {
        return new ImageClient.ImageRequest("p", 1024, 768, 1L, List.of());
    }

    private void start(int status, Map<String, String> responseHeaders, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> h = new HashMap<>();
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

    private CloudflareImageClient client() {
        return new CloudflareImageClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort() + "/client/v4",
                "acc-1", "cf-token");
    }

    private static AiProviderConfig config(String model) {
        AiProviderConfig config = new AiProviderConfig();
        config.setProviderName(CloudflareImageClient.NAME);
        config.setActiveModelName(model);
        config.setTimeoutMs(5000);
        return config;
    }
}
