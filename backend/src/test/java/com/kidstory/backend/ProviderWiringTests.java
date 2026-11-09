package com.kidstory.backend;

import com.kidstory.backend.service.ai.CloudflareImageClient;
import com.kidstory.backend.service.ai.FeatherlessLlmClient;
import com.kidstory.backend.service.ai.GeminiLlmClient;
import com.kidstory.backend.service.ai.GroqLlmClient;
import com.kidstory.backend.service.ai.ImageClient;
import com.kidstory.backend.service.ai.KikiriTtsClient;
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
        "app.ai.kikiri.enabled=true",
        "app.ai.cloudflare.account-id=acc",
        "app.ai.cloudflare.api-token=test-cf"})
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class ProviderWiringTests {

    @Autowired ApplicationContext context;

    @Test
    void everyKeyedProviderAdapterIsCreated() {
        assertThat(context.getBeansOfType(FeatherlessLlmClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(GeminiLlmClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(GroqLlmClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(KikiriTtsClient.class)).hasSize(1);
        // Kikiri açıkken taslak ses yüklenmez: servis hatası sessiz bir dosyaya düşmesin
        assertThat(context.getBeansOfType(TtsClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(CloudflareImageClient.class)).hasSize(1);
        // Cloudflare anahtarı varken taslak resim yüklenmez
        assertThat(context.getBeansOfType(ImageClient.class)).hasSize(1);
    }
}
