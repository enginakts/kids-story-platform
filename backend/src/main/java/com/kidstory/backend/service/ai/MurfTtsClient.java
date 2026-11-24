package com.kidstory.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Seslendirme: Murf AI (GEN2, POST /v1/speech/generate). Murf'te yerli Türkçe ses yoktur; çok dilli sesler
 * locale=tr-TR ile Türkçe konuşur. Ses kimliği ai_providers_config satırındaki model adıdır (ör. en-US-natalie),
 * anlatım tarzı ayardan (app.ai.murf.style). Ses base64 olarak döner: Murf sunucusunda dosya bırakılmaz.
 * Murf kelime zamanlamalarını da döndürür; bunlar sahneye [{"w":..,"s":ms,"e":ms}] olarak kaydedilir.
 */
public class MurfTtsClient implements TtsClient {

    public static final String NAME = "murf";

    private final RestClient.Builder builder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final ApiKeyRing keys;
    private final String style;

    public MurfTtsClient(RestClient.Builder builder, String baseUrl, String apiKey, String style) {
        this(builder, baseUrl, ApiKeyRing.single(NAME, apiKey), style);
    }

    /** keys: birincil ve yedek Murf hesapları (V22). */
    public MurfTtsClient(RestClient.Builder builder, String baseUrl, ApiKeyRing keys, String style) {
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.keys = keys;
        this.style = style;
    }

    @Override
    public String providerName() {
        return NAME;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.TTS;
    }

    /** Açık anahtarı kalmayan sağlayıcı seçilmez (seslendirme planlayıcısı). */
    @Override
    public boolean hasUsableKey() {
        return keys.hasUsable();
    }

    @Override
    public SpeechResult synthesize(SpeechRequest request, AiProviderConfig config) {
        return keys.call(key -> synthesize(request, config, key));
    }

    private SpeechResult synthesize(SpeechRequest request, AiProviderConfig config, ApiCredential key) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(config.getTimeoutMs()));
        RestClient client = builder.clone().requestFactory(factory).baseUrl(baseUrl).build();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", request.text());
        body.put("voiceId", request.voiceId() == null || request.voiceId().isBlank() ? config.getActiveModelName() : request.voiceId());
        body.put("locale", "tr-TR");
        if (style != null && !style.isBlank()) {
            body.put("style", style);
        }
        body.put("format", "MP3");
        body.put("sampleRate", 24000);
        body.put("encodeAsBase64", true);
        // Anlatım hızı: 1,0 normal; Murf -50..50 arası yüzde bekler (uyku masalı 0,88 → -12)
        body.put("rate", Math.max(-50, Math.min(50, (int) Math.round((request.speed() - 1.0) * 100))));
        try {
            ResponseEntity<Response> entity = client.post().uri("/v1/speech/generate")
                    .header("api-key", key.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Response.class);
            UsageMeter.status(entity.getStatusCode().value());
            Response response = entity.getBody();
            if (response == null || response.encodedAudio() == null || response.encodedAudio().isBlank()) {
                throw new IllegalStateException("Murf boş ses döndü");
            }
            byte[] audio = Base64.getDecoder().decode(response.encodedAudio());
            if (audio.length == 0) {
                throw new IllegalStateException("Murf boş ses döndü");
            }
            if (response.remainingCharacterCount() != null) {
                UsageMeter.rateLimit(NAME, Map.of("kalan-karakter", String.valueOf(response.remainingCharacterCount())));
            }
            BigDecimal seconds = response.audioLengthInSeconds() == null ? null
                    : BigDecimal.valueOf(response.audioLengthInSeconds()).setScale(2, RoundingMode.HALF_UP);
            return new SpeechResult(audio, "audio/mpeg", seconds, timestamps(response.wordDurations()));
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            UsageMeter.status(status);
            if (KeyRejectedException.rejects(status)) {
                throw new KeyRejectedException(status == HttpStatus.PAYMENT_REQUIRED.value() ? "Murf karakter kotası bitti (HTTP 402)" : "Murf anahtarı reddedildi: HTTP " + status, status);
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS.value() || status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw new RateLimitedException("Murf", OpenAiCompatibleLlmClient.retryAfter(e), status);
            }
            // 402: karakter kotası bitti. Gövde log'a yazılmaz (metin çocuğun adını içerebilir); yalnızca durum kodu.
            throw new IllegalStateException(status == HttpStatus.PAYMENT_REQUIRED.value()
                    ? "Murf karakter kotası bitti (HTTP 402)" : "Murf isteği başarısız: HTTP " + status);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Murf servisine ulaşılamadı");
        }
    }

    /** Kelime zamanları kısa JSON: [{"w":"Bir","s":0,"e":399},...]; yoksa null. */
    static String timestamps(List<WordDuration> words) {
        if (words == null || words.isEmpty()) {
            return null;
        }
        return words.stream()
                .filter(w -> w.word() != null && w.startMs() != null && w.endMs() != null)
                .map(w -> "{\"w\":\"" + w.word().replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"s\":" + w.startMs() + ",\"e\":" + w.endMs() + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String encodedAudio, Double audioLengthInSeconds, List<WordDuration> wordDurations,
                    Integer remainingCharacterCount, String warning) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WordDuration(String word, Integer startMs, Integer endMs) {
    }
}
