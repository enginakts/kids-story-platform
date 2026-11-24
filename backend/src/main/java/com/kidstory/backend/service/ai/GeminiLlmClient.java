package com.kidstory.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini, Interactions API (Google'ın varsayılan API'si). Yanıt JSON şemasına uydurulur.
 * store=false: istek ve yanıt sonradan erişim için saklanmaz. Düşünme en düşük seviyede tutulur.
 */
public class GeminiLlmClient implements LlmClient {

    public static final String NAME = "gemini";
    /** Yedek: ana model yoğunken (HTTP 503) aynı anahtarla daha hafif bir modelle sahnelere bölme (model ai_providers_config'te). */
    public static final String FALLBACK_NAME = "gemini-lite";

    private final RestClient.Builder builder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final ApiKeyRing keys;
    private final String apiRevision;
    private final String name;

    public GeminiLlmClient(RestClient.Builder builder, String baseUrl, String apiKey, String apiRevision) {
        this(NAME, builder, baseUrl, apiKey, apiRevision);
    }

    /** Aynı API'nin ikinci kaydı (ör. yedek model): sağlayıcı adı ai_providers_config satırıyla eşleşir. */
    public GeminiLlmClient(String name, RestClient.Builder builder, String baseUrl, String apiKey, String apiRevision) {
        this(name, builder, baseUrl, ApiKeyRing.single(name, apiKey), apiRevision);
    }

    /** keys: bu servisin (gemini / gemini-lite) birincil ve yedek anahtarları (V22). */
    public GeminiLlmClient(String name, RestClient.Builder builder, String baseUrl, ApiKeyRing keys, String apiRevision) {
        this.name = name;
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.keys = keys;
        this.apiRevision = apiRevision;
    }

    @Override
    public String providerName() {
        return name;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.SCENE;
    }

    /** Açık anahtarı kalmayan sağlayıcı seçilmez (seslendirme planlayıcısı). */
    @Override
    public boolean hasUsableKey() {
        return keys.hasUsable();
    }

    @Override
    public String complete(LlmRequest request, AiProviderConfig config) {
        return keys.call(key -> complete(request, config, key));
    }

    private String complete(LlmRequest request, AiProviderConfig config, ApiCredential key) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(config.getTimeoutMs()));
        RestClient client = builder.clone().requestFactory(factory).baseUrl(baseUrl).build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getActiveModelName());
        body.put("system_instruction", request.systemPrompt());
        body.put("input", request.userPrompt());
        body.put("generation_config", Map.of("thinking_level", "low", "max_output_tokens", 4096));
        if (request.schema() != null) {
            body.put("response_format", Map.of("type", "text", "mime_type", "application/json", "schema", request.schema()));
        }
        body.put("store", false);

        try {
            ResponseEntity<Interaction> entity = client.post().uri("/interactions")
                    .header("x-goog-api-key", key.secret())
                    .header("Api-Revision", apiRevision)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Interaction.class);
            Interaction response = entity.getBody();
            // Kullanım raporu (V14): yanıttaki token sayıları (düşünme tokenları çıkışa eklenir); ek çağrı yok
            UsageMeter.status(entity.getStatusCode().value());
            if (response != null && response.usage() != null) {
                Usage u = response.usage();
                UsageMeter.tokens(u.totalInputTokens(), u.totalOutputTokens() == null ? null
                        : u.totalOutputTokens() + (u.totalThoughtTokens() == null ? 0 : u.totalThoughtTokens()));
            }
            if (response == null) {
                throw new IllegalStateException("Gemini boş yanıt döndü");
            }
            if (!"completed".equals(response.status())) {
                throw new IllegalStateException("Gemini yanıtı tamamlanmadı: " + response.status());
            }
            StringBuilder text = new StringBuilder();
            if (response.steps() != null) {
                for (Step step : response.steps()) {
                    if ("model_output".equals(step.type()) && step.content() != null) {
                        step.content().stream().filter(c -> "text".equals(c.type()) && c.text() != null)
                                .forEach(c -> text.append(c.text()));
                    }
                }
            }
            if (text.isEmpty()) {
                throw new IllegalStateException("Gemini yanıtında içerik yok");
            }
            return text.toString();
        } catch (RestClientResponseException e) {
            UsageMeter.status(e.getStatusCode().value());
            if (KeyRejectedException.rejects(e.getStatusCode().value())) {
                throw new KeyRejectedException("Gemini" + " anahtarı reddedildi: HTTP " + e.getStatusCode().value(), e.getStatusCode().value());
            }
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new RateLimitedException("Gemini", OpenAiCompatibleLlmClient.retryAfter(e));
            }
            throw new IllegalStateException("Gemini isteği başarısız: HTTP " + e.getStatusCode().value());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Interaction(String status, List<Step> steps, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("total_input_tokens") Integer totalInputTokens,
                 @JsonProperty("total_output_tokens") Integer totalOutputTokens,
                 @JsonProperty("total_thought_tokens") Integer totalThoughtTokens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Step(String type, List<Content> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String type, String text) {
    }
}
