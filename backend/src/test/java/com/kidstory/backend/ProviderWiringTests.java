package com.kidstory.backend;

import com.kidstory.backend.service.ai.CloudflareImageClient;
import com.kidstory.backend.service.ai.FeatherlessLlmClient;
import com.kidstory.backend.service.ai.GeminiLlmClient;
import com.kidstory.backend.service.ai.GroqLlmClient;
import com.kidstory.backend.service.ai.ImageClient;
import com.kidstory.backend.service.ai.MurfTtsClient;
import com.kidstory.backend.service.ai.AzureSpeechTtsClient;
import com.kidstory.backend.service.ai.GeminiTtsClient;
import com.kidstory.backend.service.ai.TtsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Anahtarı verilen her sağlayıcının adaptörü uygulama bağlamında gerçekten oluşturulabilmelidir. */
@SpringBootTest(properties = {
        "app.ai.featherless.api-key=test-featherless",
        "app.ai.gemini.api-key=test-gemini",
        "app.ai.groq.api-key=test-groq",
        "app.ai.azure-speech.key=test-azure",
        "app.ai.murf.api-key=test-murf",
        "app.ai.cloudflare.account-id=acc",
        "app.ai.cloudflare.api-token=test-cf"})
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class ProviderWiringTests {

    @Autowired ApplicationContext context;

    @Test
    void everyKeyedProviderAdapterIsCreated() {
        assertThat(context.getBeansOfType(FeatherlessLlmClient.class)).hasSize(1);
        // Ana Gemini ve yoğunlukta devreye giren yedek kayıt (gemini-lite)
        assertThat(context.getBeansOfType(GeminiLlmClient.class)).hasSize(2);
        assertThat(context.getBeansOfType(GroqLlmClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(GeminiTtsClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(AzureSpeechTtsClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(MurfTtsClient.class)).hasSize(1);
        // Ses anahtarı varken taslak ses yüklenmez: servis hatası sessiz bir dosyaya düşmesin
        assertThat(context.getBeansOfType(TtsClient.class)).hasSize(3);
        assertThat(context.getBeansOfType(CloudflareImageClient.class)).hasSize(1);
        // Cloudflare anahtarı varken taslak resim yüklenmez
        assertThat(context.getBeansOfType(ImageClient.class)).hasSize(1);
    }
}
