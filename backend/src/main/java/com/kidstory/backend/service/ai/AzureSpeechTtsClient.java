package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Seslendirme: Microsoft Azure AI Speech, REST (SSML). Ücretsiz katman (F0) ayda 0,5 milyon karakter verir;
 * kota dolunca servis 429/403 döndürür, ücret çıkmaz. Ses adı ai_providers_config satırındaki model adıdır
 * (ör. tr-TR-EmelNeural). Anlatım hızı SSML prosody ile uygulanır. Çıktı 48 kbit/s sabit hızlı MP3.
 */
public class AzureSpeechTtsClient implements TtsClient {

    public static final String NAME = "azure";
    private static final String OUTPUT = "audio-24khz-48kbitrate-mono-mp3";
    private static final int BITS_PER_SECOND = 48_000;

    private final RestClient.Builder builder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final ApiKeyRing keys;

    /** baseUrl: https://{bölge}.tts.speech.microsoft.com */
    public AzureSpeechTtsClient(RestClient.Builder builder, String baseUrl, String key) {
        this(builder, baseUrl, ApiKeyRing.single(NAME, key));
    }

    /** keys: birincil ve yedek Azure kaynakları; her yedeğin bölgesi kendi adresindedir (V22). */
    public AzureSpeechTtsClient(RestClient.Builder builder, String baseUrl, ApiKeyRing keys) {
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.keys = keys;
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
        String url = key.baseUrl() == null || key.baseUrl().isBlank() ? baseUrl : key.baseUrl().replaceAll("/+$", "");
        RestClient client = builder.clone().requestFactory(factory).baseUrl(url).build();
        try {
            byte[] audio = client.post().uri("/cognitiveservices/v1")
                    .header("Ocp-Apim-Subscription-Key", key.secret())
                    .header("X-Microsoft-OutputFormat", OUTPUT)
                    .header("User-Agent", "masal-dunyasi")
                    .contentType(MediaType.parseMediaType("application/ssml+xml; charset=utf-8"))
                    .body(ssml(request.text(), config.getActiveModelName(), request.speed()).getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .body(byte[].class);
            UsageMeter.status(200);
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("Azure boş ses döndü");
            }
            // Sabit bit hızlı MP3: süre = bayt × 8 / bit hızı
            BigDecimal seconds = BigDecimal.valueOf(audio.length * 8L).divide(BigDecimal.valueOf(BITS_PER_SECOND), 2, RoundingMode.HALF_UP);
            return new SpeechResult(audio, "audio/mpeg", seconds, null);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            UsageMeter.status(status);
            if (KeyRejectedException.rejects(status)) {
                throw new KeyRejectedException("Azure Speech anahtarı reddedildi: HTTP " + status, status);
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS.value() || status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw new RateLimitedException("Azure Speech", OpenAiCompatibleLlmClient.retryAfter(e), status);
            }
            // Gövde log'a yazılmaz (metin çocuğun adını içerebilir); yalnızca durum kodu.
            throw new IllegalStateException("Azure Speech isteği başarısız: HTTP " + status);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Azure Speech servisine ulaşılamadı");
        }
    }

    static String ssml(String text, String voice, double speed) {
        int percent = (int) Math.round((speed - 1.0) * 100);
        String rate = (percent >= 0 ? "+" : "") + percent + "%";
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"tr-TR\">"
                + "<voice name=\"" + escape(voice) + "\"><prosody rate=\"" + rate + "\">" + escape(text) + "</prosody></voice></speak>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
