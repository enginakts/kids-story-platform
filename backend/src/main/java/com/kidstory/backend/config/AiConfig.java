package com.kidstory.backend.config;

import com.kidstory.backend.service.ai.CloudflareImageClient;
import com.kidstory.backend.service.ai.FeatherlessLlmClient;
import com.kidstory.backend.service.ai.GeminiLlmClient;
import com.kidstory.backend.service.ai.GroqLlmClient;
import com.kidstory.backend.service.ai.KikiriTtsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Anahtarı verilen sağlayıcının adaptörü yüklenir. Hangi sağlayıcının hangi sırayla kullanılacağı
 * ai_providers_config tablosundan gelir. Arka plan üretim işçisi için zamanlayıcı da burada açılır.
 */
@Configuration
@EnableScheduling
public class AiConfig {

    @Bean
    @ConditionalOnExpression("'${app.ai.featherless.api-key:}' != ''")
    FeatherlessLlmClient featherlessLlmClient(RestClient.Builder builder, @Value("${app.ai.featherless.base-url}") String baseUrl,
                                              @Value("${app.ai.featherless.api-key}") String apiKey) {
        return new FeatherlessLlmClient(builder, baseUrl, apiKey);
    }

    @Bean
    @ConditionalOnExpression("'${app.ai.gemini.api-key:}' != ''")
    GeminiLlmClient geminiLlmClient(RestClient.Builder builder, @Value("${app.ai.gemini.base-url}") String baseUrl,
                                    @Value("${app.ai.gemini.api-key}") String apiKey,
                                    @Value("${app.ai.gemini.api-revision}") String apiRevision) {
        return new GeminiLlmClient(builder, baseUrl, apiKey, apiRevision);
    }

    /** Sahne resimleri: Cloudflare Workers AI üzerinde FLUX (model adı ai_providers_config'ten). */
    @Bean
    @ConditionalOnExpression("'${app.ai.cloudflare.api-token:}' != ''")
    CloudflareImageClient cloudflareImageClient(RestClient.Builder builder, @Value("${app.ai.cloudflare.base-url}") String baseUrl,
                                                @Value("${app.ai.cloudflare.account-id}") String accountId,
                                                @Value("${app.ai.cloudflare.api-token}") String apiToken) {
        return new CloudflareImageClient(builder, baseUrl, accountId, apiToken);
    }

    /** Seslendirme: kendi çalıştırdığımız Kikiri TTS servisi (tts-service). Kapatılırsa geliştirmede taslak ses kullanılır. */
    @Bean
    @ConditionalOnExpression("${app.ai.kikiri.enabled:true}")
    KikiriTtsClient kikiriTtsClient(RestClient.Builder builder, @Value("${app.ai.kikiri.base-url}") String baseUrl,
                                    @Value("${app.ai.kikiri.token:}") String token,
                                    @Value("${app.ai.kikiri.hf-token:}") String hfToken) {
        return new KikiriTtsClient(builder, baseUrl, token, hfToken);
    }

    @Bean
    @ConditionalOnExpression("'${app.ai.groq.api-key:}' != ''")
    GroqLlmClient groqLlmClient(RestClient.Builder builder, @Value("${app.ai.groq.base-url}") String baseUrl,
                                @Value("${app.ai.groq.api-key}") String apiKey) {
        return new GroqLlmClient(builder, baseUrl, apiKey);
    }
}
