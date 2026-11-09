package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kikiri ses servisi adaptörünün istek/yanıt sözleşmesi, sahte yerel HTTP sunucusuyla. */
class KikiriTtsClientTests {

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
    void sendsTextVoiceSpeedAndTokensAndReadsMp3WithDuration() throws IOException {
        start(200, Map.of("X-Audio-Duration", "4.37", "Content-Type", "audio/mpeg"), "MP3DATA".getBytes(StandardCharsets.UTF_8));
        TtsClient.SpeechResult result = client("svc-token", "hf_abc")
                .synthesize(new TtsClient.SpeechRequest("Ada'ya bir sepet çilek geldi.", "nisan", 0.95), config());

        assertThat(new String(result.audio(), StandardCharsets.UTF_8)).isEqualTo("MP3DATA");
        assertThat(result.mimeType()).isEqualTo("audio/mpeg");
        assertThat(result.durationSeconds()).isEqualByComparingTo(new BigDecimal("4.37"));
        assertThat(headers.get().get("X-tts-token")).isEqualTo("svc-token");
        // uvicorn h2c yükseltmesini reddeder; istek düz HTTP/1.1 olmalı
        assertThat(headers.get()).doesNotContainKeys("Upgrade", "Http2-settings");
        assertThat(headers.get().get("Authorization")).isEqualTo("Bearer hf_abc");
        assertThat(body.get()).contains("\"text\":\"Ada'ya bir sepet çilek geldi.\"").contains("\"voice\":\"nisan\"")
                .contains("\"speed\":0.95").contains("\"format\":\"mp3\"");
    }

    @Test
    void noTokensMeansNoAuthHeaders() throws IOException {
        start(200, Map.of("Content-Type", "audio/mpeg"), new byte[]{1, 2, 3});
        TtsClient.SpeechResult result = client("", "").synthesize(new TtsClient.SpeechRequest("Merhaba.", "nisan", 1.0), config());
        assertThat(result.durationSeconds()).isNull();
        assertThat(headers.get()).doesNotContainKeys("X-tts-token", "Authorization");
    }

    @Test
    void sleepingSpaceIsRetriedLater() throws IOException {
        start(503, Map.of("retry-after", "20"), "{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> client("", "").synthesize(new TtsClient.SpeechRequest("Merhaba.", "nisan", 1.0), config()))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(20));
    }

    @Test
    void errorsDoNotLeakTheTextAndUnreachableServiceIsNamed() throws IOException {
        start(400, Map.of(), "{\"detail\":\"Ada'ya bir sepet\"}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> client("", "").synthesize(new TtsClient.SpeechRequest("Ada'ya bir sepet", "nisan", 1.0), config()))
                .hasMessage("Kikiri ses servisi isteği başarısız: HTTP 400");
        server.stop(0);
        assertThatThrownBy(() -> client("", "").synthesize(new TtsClient.SpeechRequest("Merhaba.", "nisan", 1.0), config()))
                .hasMessageStartingWith("Kikiri ses servisine ulaşılamadı");
        server = null;
    }

    @Test
    void emptyAudioIsRejected() throws IOException {
        start(200, Map.of("Content-Type", "audio/mpeg"), new byte[0]);
        assertThatThrownBy(() -> client("", "").synthesize(new TtsClient.SpeechRequest("Merhaba.", "nisan", 1.0), config()))
                .hasMessageContaining("boş ses");
    }

    private void start(int status, Map<String, String> responseHeaders, byte[] response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tts", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> h = new HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> h.put(k, v.getFirst()));
            headers.set(h);
            responseHeaders.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
            if (response.length > 0) {
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
    }

    private KikiriTtsClient client(String token, String hfToken) {
        return new KikiriTtsClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), token, hfToken);
    }

    private static AiProviderConfig config() {
        AiProviderConfig config = new AiProviderConfig();
        config.setProviderName(KikiriTtsClient.NAME);
        config.setActiveModelName("duxx/kikiri-turkish-base (nisan)");
        config.setTimeoutMs(5000);
        return config;
    }
}
