package com.kidstory.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private final RestClient.Builder builder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String apiKey;
    private final String apiRevision;

    public GeminiLlmClient(RestClient.Builder builder, String baseUrl, String apiKey, String apiRevision) {
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.apiRevision = apiRevision;
    }

    @Override
    public String providerName() {
        return NAME;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.SCENE;
    }

    @Override
    public String complete(LlmRequest request, AiProviderConfig config) {
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
            Interaction response = client.post().uri("/interactions")
                    .header("x-goog-api-key", apiKey)
                    .header("Api-Revision", apiRevision)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Interaction.class);
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
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new RateLimitedException("Gemini", OpenAiCompatibleLlmClient.retryAfter(e));
            }
            throw new IllegalStateException("Gemini isteği başarısız: HTTP " + e.getStatusCode().value());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Interaction(String status, List<Step> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Step(String type, List<Content> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String type, String text) {
    }
}
