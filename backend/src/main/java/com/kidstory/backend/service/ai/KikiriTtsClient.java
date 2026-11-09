package com.kidstory.backend.service.ai;

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
import java.util.Map;

/**
 * Kikiri Türkçe TTS (Kokoro-82M tabanlı, ücretsiz model). Model bizim çalıştırdığımız tts-service üzerinden
 * kullanılır: yerel sunucu veya bize ait özel bir Hugging Face Space. Metin üçüncü taraf bir API'ye gitmez.
 */
public class KikiriTtsClient implements TtsClient {

    public static final String NAME = "kikiri";

    private final RestClient.Builder builder;
    // HTTP/1.1 sabit: JDK istemcisi düz http'de HTTP/2'ye yükseltme (h2c) dener, uvicorn bunu reddedip isteği bozar.
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String serviceToken;
    private final String hfToken;

    public KikiriTtsClient(RestClient.Builder builder, String baseUrl, String serviceToken, String hfToken) {
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.serviceToken = serviceToken;
        this.hfToken = hfToken;
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
        try {
            ResponseEntity<byte[]> response = client.post().uri("/v1/tts")
                    .headers(h -> {
                        if (!serviceToken.isBlank()) {
                            h.set("X-TTS-Token", serviceToken);
                        }
                        // Özel (private) Hugging Face Space'e erişim
                        if (!hfToken.isBlank()) {
                            h.setBearerAuth(hfToken);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("audio/mpeg"))
                    .body(Map.of("text", request.text(), "voice", request.voiceId(), "speed", request.speed(), "format", "mp3"))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] audio = response.getBody();
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("Kikiri boş ses döndü");
            }
            return new SpeechResult(audio, "audio/mpeg", duration(response.getHeaders().getFirst("X-Audio-Duration")), null);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // 503: Space uyuyor veya model yükleniyor; 429: yoğunluk. İkisinde de bir süre sonra yeniden denenir.
            if (status == HttpStatus.SERVICE_UNAVAILABLE.value() || status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new RateLimitedException("Kikiri ses servisi", OpenAiCompatibleLlmClient.retryAfter(e));
            }
            // Gövde log'a yazılmaz (metin çocuğun adını içerebilir); yalnızca durum kodu.
            throw new IllegalStateException("Kikiri ses servisi isteği başarısız: HTTP " + status);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Kikiri ses servisine ulaşılamadı (" + baseUrl + ")");
        }
    }

    private static BigDecimal duration(String header) {
        try {
            return header == null ? null : new BigDecimal(header).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
