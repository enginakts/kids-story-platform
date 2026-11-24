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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini TTS ve Azure Speech adaptörlerinin istek/yanıt sözleşmesi, sahte yerel HTTP sunucusuyla. */
class TtsClientsTests {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> headers = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---------- Gemini ----------

    @Test
    void geminiSendsTextVoiceAndKeyAndWrapsPcmIntoWav() throws IOException {
        byte[] pcm = new byte[48_000]; // 24 kHz, 16 bit, mono: 1 saniye
        start(200, Map.of("Content-Type", "application/json"), geminiAudio("audio/L16;codec=pcm;rate=24000", pcm));
        TtsClient.SpeechResult result = gemini().synthesize(new TtsClient.SpeechRequest("Ada'ya bir sepet çilek geldi.", null, 0.88),
                config("gemini-3.8-flash-lite-tts"));

        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-3.8-flash-lite-tts:generateContent");
        assertThat(headers.get().get("X-goog-api-key")).isEqualTo("test-key");
        assertThat(body.get()).contains("\"text\":\"Ada'ya bir sepet çilek geldi.\"").contains("\"voiceName\":\"Kore\"")
                .contains("\"responseModalities\":[\"AUDIO\"]");
        assertThat(result.mimeType()).isEqualTo("audio/wav");
        assertThat(new String(result.audio(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(result.audio()).hasSize(44 + pcm.length);
        assertThat(result.durationSeconds()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void geminiKeepsWavAsIsAndReadsItsDuration() throws IOException {
        byte[] wav = Wav.fromPcm16(new byte[96_000], 24_000, 1); // 2 saniye
        start(200, Map.of("Content-Type", "application/json"), geminiAudio("audio/wav", wav));
        TtsClient.SpeechResult result = gemini().synthesize(new TtsClient.SpeechRequest("Merhaba.", "Puck", 1.0), config("m"));
        assertThat(result.audio()).isEqualTo(wav);
        assertThat(result.durationSeconds()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(body.get()).contains("\"voiceName\":\"Puck\"");
    }

    @Test
    void geminiQuotaIsRateLimitedSoTheRouterMovesOn() throws IOException {
        start(429, Map.of("retry-after", "40"), "{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> gemini().synthesize(new TtsClient.SpeechRequest("Merhaba.", null, 1.0), config("m")))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(40));
    }

    @Test
    void geminiErrorsDoNotLeakTheTextAndMissingAudioIsRejected() throws IOException {
        start(400, Map.of(), "{\"error\":\"Ada'ya bir sepet\"}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> gemini().synthesize(new TtsClient.SpeechRequest("Ada'ya bir sepet", null, 1.0), config("m")))
                .hasMessage("Gemini TTS isteği başarısız: HTTP 400");
        server.stop(0);
        start(200, Map.of("Content-Type", "application/json"), "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> gemini().synthesize(new TtsClient.SpeechRequest("Merhaba.", null, 1.0), config("m")))
                .hasMessage("Gemini TTS yanıtında ses yok");
    }

    // ---------- Azure ----------

    @Test
    void azureSendsSsmlWithVoiceRateAndEscapedTextAndReadsMp3() throws IOException {
        byte[] mp3 = new byte[12_000]; // 48 kbit/s: 2 saniye
        start(200, Map.of("Content-Type", "audio/mpeg"), mp3);
        TtsClient.SpeechResult result = azure().synthesize(new TtsClient.SpeechRequest("Pamuk & Leo <birlikte> \"oynadı\".", null, 0.88),
                config("tr-TR-EmelNeural"));

        assertThat(path.get()).isEqualTo("/cognitiveservices/v1");
        assertThat(headers.get().get("Ocp-apim-subscription-key")).isEqualTo("azure-key");
        assertThat(headers.get().get("X-microsoft-outputformat")).isEqualTo("audio-24khz-48kbitrate-mono-mp3");
        assertThat(headers.get().get("Content-type")).startsWith("application/ssml+xml");
        assertThat(body.get()).contains("xml:lang=\"tr-TR\"").contains("<voice name=\"tr-TR-EmelNeural\">")
                .contains("<prosody rate=\"-12%\">").contains("Pamuk &amp; Leo &lt;birlikte&gt; &quot;oynadı&quot;.");
        assertThat(result.mimeType()).isEqualTo("audio/mpeg");
        assertThat(result.durationSeconds()).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    void azureQuotaIsRateLimitedAndOtherErrorsAreNamed() throws IOException {
        start(429, Map.of("retry-after", "60"), new byte[0]);
        assertThatThrownBy(() -> azure().synthesize(new TtsClient.SpeechRequest("Merhaba.", null, 1.0), config("tr-TR-EmelNeural")))
                .isInstanceOf(RateLimitedException.class);
        server.stop(0);
        start(401, Map.of(), new byte[0]);
        assertThatThrownBy(() -> azure().synthesize(new TtsClient.SpeechRequest("Merhaba.", null, 1.0), config("tr-TR-EmelNeural")))
                .hasMessage("Azure Speech anahtarı reddedildi: HTTP 401"); // V22: anahtar halkası bu anahtarı kapatır
        server.stop(0);
        server = null;
        assertThatThrownBy(() -> azure().synthesize(new TtsClient.SpeechRequest("Merhaba.", null, 1.0), config("tr-TR-EmelNeural")))
                .hasMessage("Azure Speech servisine ulaşılamadı");
    }

    @Test
    void ssmlRateFollowsNarrationSpeed() {
        assertThat(AzureSpeechTtsClient.ssml("a", "v", 1.0)).contains("rate=\"+0%\"");
        assertThat(AzureSpeechTtsClient.ssml("a", "v", 0.95)).contains("rate=\"-5%\"");
    }

    // ---------- Murf ----------

    @Test
    void murfSendsTurkishLocaleVoiceStyleRateAndReadsBase64Mp3WithWordTimes() throws IOException {
        byte[] mp3 = "ID3-murf".getBytes(StandardCharsets.UTF_8);
        start(200, Map.of("Content-Type", "application/json"), ("{\"encodedAudio\":\"" + Base64.getEncoder().encodeToString(mp3)
                + "\",\"audioLengthInSeconds\":8.731587,\"remainingCharacterCount\":333204,\"wordDurations\":["
                + "{\"word\":\"Bir\",\"startMs\":0,\"endMs\":399},{\"word\":\"varmış\",\"startMs\":399,\"endMs\":798}]}")
                .getBytes(StandardCharsets.UTF_8));
        TtsClient.SpeechResult result = murf().synthesize(new TtsClient.SpeechRequest("Bir varmış.", null, 0.88), config("en-US-natalie"));

        assertThat(path.get()).isEqualTo("/v1/speech/generate");
        assertThat(headers.get().get("Api-key")).isEqualTo("murf-key");
        assertThat(body.get()).contains("\"text\":\"Bir varmış.\"").contains("\"voiceId\":\"en-US-natalie\"").contains("\"locale\":\"tr-TR\"")
                .contains("\"style\":\"Narration\"").contains("\"rate\":-12").contains("\"encodeAsBase64\":true").contains("\"format\":\"MP3\"");
        assertThat(result.audio()).isEqualTo(mp3);
        assertThat(result.mimeType()).isEqualTo("audio/mpeg");
        assertThat(result.durationSeconds()).isEqualByComparingTo(new BigDecimal("8.73"));
        assertThat(result.timestampsJson()).isEqualTo("[{\"w\":\"Bir\",\"s\":0,\"e\":399},{\"w\":\"varmış\",\"s\":399,\"e\":798}]");
    }

    @Test
    void murfQuotaAndErrorsAreNamedWithoutTheText() throws IOException {
        start(402, Map.of(), "{\"errorMessage\":\"Bir varmış\"}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> murf().synthesize(new TtsClient.SpeechRequest("Bir varmış.", null, 1.0), config("en-US-natalie")))
                .hasMessage("Murf karakter kotası bitti (HTTP 402)");
        server.stop(0);
        start(429, Map.of("retry-after", "30"), new byte[0]);
        assertThatThrownBy(() -> murf().synthesize(new TtsClient.SpeechRequest("Bir varmış.", null, 1.0), config("en-US-natalie")))
                .isInstanceOf(RateLimitedException.class);
        server.stop(0);
        start(200, Map.of("Content-Type", "application/json"), "{\"audioFile\":\"https://x\"}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> murf().synthesize(new TtsClient.SpeechRequest("Bir varmış.", null, 1.0), config("en-US-natalie")))
                .hasMessage("Murf boş ses döndü");
    }

    // ---------- yardımcılar ----------

    private MurfTtsClient murf() {
        return new MurfTtsClient(RestClient.builder(), "http://localhost:" + port, "murf-key", "Narration");
    }

    private int port = 0;

    private GeminiTtsClient gemini() {
        return new GeminiTtsClient(RestClient.builder(), "http://localhost:" + port + "/v1beta", "test-key", "Kore");
    }

    private AzureSpeechTtsClient azure() {
        return new AzureSpeechTtsClient(RestClient.builder(), "http://localhost:" + port, "azure-key");
    }

    private static byte[] geminiAudio(String mime, byte[] audio) {
        return ("{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"" + mime + "\",\"data\":\""
                + Base64.getEncoder().encodeToString(audio) + "\"}}]}}],\"usageMetadata\":{\"promptTokenCount\":12,\"candidatesTokenCount\":300}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static AiProviderConfig config(String model) {
        AiProviderConfig c = new AiProviderConfig();
        c.setActiveModelName(model);
        c.setTimeoutMs(5000);
        return c;
    }

    private void start(int status, Map<String, String> responseHeaders, byte[] response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> h = new HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> h.put(k, v.getFirst()));
            headers.set(h);
            responseHeaders.forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
            exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
            if (response.length > 0) {
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
    }
}
