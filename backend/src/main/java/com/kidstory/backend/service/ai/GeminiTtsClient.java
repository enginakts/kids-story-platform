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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seslendirme: Google Gemini TTS (generateContent, yanıt türü AUDIO). Türkçeyi kendiliğinden tanır.
 * Model adı ai_providers_config satırından, ses adı ayardan (app.ai.gemini.tts-voice) gelir.
 * Not: Bu modeller talimatı (sistem mesajı) kabul etmez; metnin başına yazılan "sakin oku" gibi bir istek
 * de sesli okunur. Bu yüzden yalnızca masal metni gönderilir; anlatım hızı bu sağlayıcıda uygulanmaz.
 */
public class GeminiTtsClient implements TtsClient {

    public static final String NAME = "gemini-tts";
    private static final Pattern RATE = Pattern.compile("rate=(\\d+)");

    private final RestClient.Builder builder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String apiKey;
    private final String voice;

    public GeminiTtsClient(RestClient.Builder builder, String baseUrl, String apiKey, String voice) {
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.voice = voice;
    }

    @Override
    public String providerName() {
        return NAME;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.TTS;
    }

    @Override
    public SpeechResult synthesize(SpeechRequest request, AiProviderConfig config) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(config.getTimeoutMs()));
        RestClient client = builder.clone().requestFactory(factory).baseUrl(baseUrl).build();
        String voiceName = request.voiceId() == null || request.voiceId().isBlank() ? voice : request.voiceId();
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", request.text())))),
                "generationConfig", Map.of("responseModalities", List.of("AUDIO"),
                        "speechConfig", Map.of("voiceConfig", Map.of("prebuiltVoiceConfig", Map.of("voiceName", voiceName)))));
        try {
            ResponseEntity<Response> entity = client.post().uri("/models/{model}:generateContent", config.getActiveModelName())
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Response.class);
            UsageMeter.status(entity.getStatusCode().value());
            Response response = entity.getBody();
            if (response != null && response.usageMetadata() != null) {
                UsageMeter.tokens(response.usageMetadata().promptTokenCount(), response.usageMetadata().candidatesTokenCount());
            }
            InlineData data = audioPart(response);
            byte[] audio = Base64.getDecoder().decode(data.data());
            if (audio.length == 0) {
                throw new IllegalStateException("Gemini boş ses döndü");
            }
            String mime = data.mimeType() == null ? "" : data.mimeType().toLowerCase(Locale.ROOT);
            // Yeni modeller WAV döndürür; eski modeller başlıksız 16 bit PCM (audio/L16) döndürür, WAV'a sarılır.
            byte[] wav = mime.startsWith("audio/wav") || mime.startsWith("audio/x-wav") ? audio : Wav.fromPcm16(audio, sampleRate(mime), 1);
            return new SpeechResult(wav, "audio/wav", Wav.durationSeconds(wav), null);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            UsageMeter.status(status);
            // 429: ücretsiz kota (dakikalık/günlük) doldu; yönlendirici sıradaki sağlayıcıya geçer, iş sonra yeniden denenir.
            if (status == HttpStatus.TOO_MANY_REQUESTS.value() || status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw new RateLimitedException("Gemini TTS", OpenAiCompatibleLlmClient.retryAfter(e));
            }
            // Gövde log'a yazılmaz (metin çocuğun adını içerebilir); yalnızca durum kodu.
            throw new IllegalStateException("Gemini TTS isteği başarısız: HTTP " + status);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Gemini TTS servisine ulaşılamadı");
        }
    }

    private static InlineData audioPart(Response response) {
        if (response == null || response.candidates() == null) {
            throw new IllegalStateException("Gemini TTS boş yanıt döndü");
        }
        for (Candidate c : response.candidates()) {
            if (c.content() == null || c.content().parts() == null) {
                continue;
            }
            for (Part p : c.content().parts()) {
                if (p.inlineData() != null && p.inlineData().data() != null) {
                    return p.inlineData();
                }
            }
        }
        throw new IllegalStateException("Gemini TTS yanıtında ses yok");
    }

    static int sampleRate(String mime) {
        Matcher m = RATE.matcher(mime);
        return m.find() ? Integer.parseInt(m.group(1)) : 24000;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Candidate> candidates, UsageMetadata usageMetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(InlineData inlineData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InlineData(String mimeType, String data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount) {
    }
}
