package com.kidstory.backend.config;

import com.kidstory.backend.service.ApiKeyRegistry;
import com.kidstory.backend.service.ai.CloudflareImageClient;
import com.kidstory.backend.service.ai.FeatherlessLlmClient;
import com.kidstory.backend.service.ai.GeminiLlmClient;
import com.kidstory.backend.service.ai.GroqLlmClient;
import com.kidstory.backend.service.ai.MurfTtsClient;
import com.kidstory.backend.service.ai.AzureSpeechTtsClient;
import com.kidstory.backend.service.ai.GeminiTtsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Anahtarı verilen sağlayıcının adaptörü yüklenir. Birincil ve yedek anahtarlar (GEMINI_API_KEY, GEMINI_API_KEY_2 ...)
 * ApiKeyRegistry'den gelir; anahtar kota/yetki hatasında otomatik kapanır, yedeğe geçilir (V22). Hangi sağlayıcının hangi sırayla kullanılacağı
 * ai_providers_config tablosundan gelir. Arka plan üretim işçisi için zamanlayıcı da burada açılır.
 */
@Configuration
@EnableScheduling
public class AiConfig {

    @Bean
    @ConditionalOnExpression("'${app.ai.featherless.api-key:}' != ''")
    FeatherlessLlmClient featherlessLlmClient(RestClient.Builder builder, @Value("${app.ai.featherless.base-url}") String baseUrl,
                                              ApiKeyRegistry keys) {
        return new FeatherlessLlmClient(builder, baseUrl, keys.ring(FeatherlessLlmClient.NAME));
    }

    @Bean
    @ConditionalOnExpression("'${app.ai.gemini.api-key:}' != ''")
    GeminiLlmClient geminiLlmClient(RestClient.Builder builder, @Value("${app.ai.gemini.base-url}") String baseUrl,
                                    ApiKeyRegistry keys, @Value("${app.ai.gemini.api-revision}") String apiRevision) {
        return new GeminiLlmClient(GeminiLlmClient.NAME, builder, baseUrl, keys.ring(GeminiLlmClient.NAME), apiRevision);
    }

    /** Sahnelere bölmede yedek: ana Gemini modeli yoğunken (503) yönlendirici bu kayda geçer (V13, öncelik 2). */
    @Bean
    @ConditionalOnExpression("'${app.ai.gemini.api-key:}' != ''")
    GeminiLlmClient geminiFallbackLlmClient(RestClient.Builder builder, @Value("${app.ai.gemini.base-url}") String baseUrl,
                                            ApiKeyRegistry keys, @Value("${app.ai.gemini.api-revision}") String apiRevision) {
        return new GeminiLlmClient(GeminiLlmClient.FALLBACK_NAME, builder, baseUrl, keys.ring(GeminiLlmClient.FALLBACK_NAME), apiRevision);
    }

    /** Sahne resimleri: Cloudflare Workers AI üzerinde FLUX (model adı ai_providers_config'ten). */
    @Bean
    @ConditionalOnExpression("'${app.ai.cloudflare.api-token:}' != ''")
    CloudflareImageClient cloudflareImageClient(RestClient.Builder builder, @Value("${app.ai.cloudflare.base-url}") String baseUrl,
                                                @Value("${app.ai.cloudflare.account-id}") String accountId, ApiKeyRegistry keys) {
        return new CloudflareImageClient(builder, baseUrl, accountId, keys.ring(CloudflareImageClient.NAME));
    }

    /** Seslendirme (1. sıra): Gemini TTS, sahnelere bölmeyle aynı Gemini anahtarı. Model adı ai_providers_config'ten. */
    @Bean
    @ConditionalOnExpression("'${app.ai.gemini.api-key:}' != ''")
    GeminiTtsClient geminiTtsClient(RestClient.Builder builder, @Value("${app.ai.gemini.base-url}") String baseUrl,
                                    ApiKeyRegistry keys, @Value("${app.ai.gemini.tts-voice}") String voice) {
        return new GeminiTtsClient(builder, baseUrl, keys.ring(GeminiTtsClient.NAME), voice);
    }

    /** Seslendirme (2. sıra): Murf AI. Çok dilli ses Türkçe konuşur; ses kimliği ai_providers_config'ten. */
    @Bean
    @ConditionalOnExpression("'${app.ai.murf.api-key:}' != ''")
    MurfTtsClient murfTtsClient(RestClient.Builder builder, @Value("${app.ai.murf.base-url}") String baseUrl,
                                ApiKeyRegistry keys, @Value("${app.ai.murf.style:}") String style) {
        return new MurfTtsClient(builder, baseUrl, keys.ring(MurfTtsClient.NAME), style);
    }

    /** Seslendirme (3. sıra): Azure AI Speech. Önceki sağlayıcılar kota dolunca ya da hata verince devreye girer. Ses adı ai_providers_config'ten. */
    @Bean
    @ConditionalOnExpression("'${app.ai.azure-speech.key:}' != ''")
    AzureSpeechTtsClient azureSpeechTtsClient(RestClient.Builder builder, @Value("${app.ai.azure-speech.base-url}") String baseUrl,
                                              ApiKeyRegistry keys) {
        return new AzureSpeechTtsClient(builder, baseUrl, keys.ring(AzureSpeechTtsClient.NAME));
    }

    @Bean
    @ConditionalOnExpression("'${app.ai.groq.api-key:}' != ''")
    GroqLlmClient groqLlmClient(RestClient.Builder builder, @Value("${app.ai.groq.base-url}") String baseUrl,
                                ApiKeyRegistry keys) {
        return new GroqLlmClient(builder, baseUrl, keys.ring(GroqLlmClient.NAME));
    }
}
