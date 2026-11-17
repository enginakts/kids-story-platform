package com.kidstory.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.http.HttpHeaders;
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
import java.util.Locale;
import java.util.Map;

/** OpenAI uyumlu /chat/completions sağlayıcıları (Groq, Featherless) için ortak adaptör. */
public abstract class OpenAiCompatibleLlmClient implements LlmClient {

    private final String name;
    private final String displayName;
    private final RestClient.Builder builder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String apiKey;

    protected OpenAiCompatibleLlmClient(String name, String displayName, RestClient.Builder builder, String baseUrl, String apiKey) {
        this.name = name;
        this.displayName = displayName;
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
    }

    @Override
    public String providerName() {
        return name;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.LLM;
    }

    /** Sağlayıcıya özgü alanlar (yanıt biçimi, akıl yürütme ayarları). */
    protected abstract void customize(Map<String, Object> body, LlmRequest request);

    /** Yanıt metnine sağlayıcıya özgü son işlem. */
    protected String postProcess(String content) {
        return content;
    }

    @Override
    public String complete(LlmRequest request, AiProviderConfig config) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(config.getTimeoutMs()));
        RestClient client = builder.clone().requestFactory(factory).baseUrl(baseUrl).build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getActiveModelName());
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        body.put("temperature", 0.8);
        customize(body, request);

        try {
            ResponseEntity<ChatResponse> entity = client.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(ChatResponse.class);
            ChatResponse response = entity.getBody();
            // Kullanım raporu (V14): yanıtta zaten gelen token sayıları ve kalan kota başlıkları; ek çağrı yok
            UsageMeter.status(entity.getStatusCode().value());
            meterRateLimit(entity.getHeaders());
            if (response != null && response.usage() != null) {
                UsageMeter.tokens(response.usage().promptTokens(), response.usage().completionTokens());
            }
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new IllegalStateException(displayName + " boş yanıt döndü");
            }
            Choice choice = response.choices().getFirst();
            if ("length".equals(choice.finishReason())) {
                throw new IllegalStateException(displayName + " yanıtı token sınırında kesildi");
            }
            String content = choice.message() == null ? null : choice.message().content();
            content = content == null ? null : postProcess(content);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException(displayName + " yanıtında içerik yok");
            }
            return content;
        } catch (RestClientResponseException e) {
            UsageMeter.status(e.getStatusCode().value());
            meterRateLimit(e.getResponseHeaders());
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new RateLimitedException(displayName, retryAfter(e));
            }
            // Gövde ve anahtar log'a yazılmaz; yalnızca durum kodu.
            throw new IllegalStateException(displayName + " isteği başarısız: HTTP " + e.getStatusCode().value());
        }
    }

    private void meterRateLimit(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        Map<String, String> limits = new LinkedHashMap<>();
        headers.forEach((k, v) -> {
            if (k.toLowerCase(Locale.ROOT).startsWith("x-ratelimit-") && !v.isEmpty()) {
                limits.put(k.toLowerCase(Locale.ROOT), v.getFirst());
            }
        });
        UsageMeter.rateLimit(name, limits);
    }

    static long retryAfter(RestClientResponseException e) {
        String value = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("retry-after");
        try {
            return value == null ? 30 : Math.max(1, (long) Math.ceil(Double.parseDouble(value)));
        } catch (NumberFormatException ex) {
            return 30;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") Integer promptTokens, @JsonProperty("completion_tokens") Integer completionTokens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }
}
