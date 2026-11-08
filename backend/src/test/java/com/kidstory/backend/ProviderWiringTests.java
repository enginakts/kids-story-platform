package com.kidstory.backend;

import com.kidstory.backend.service.ai.FeatherlessLlmClient;
import com.kidstory.backend.service.ai.GeminiLlmClient;
import com.kidstory.backend.service.ai.GroqLlmClient;
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
        "app.ai.groq.api-key=test-groq"})
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class ProviderWiringTests {

    @Autowired ApplicationContext context;

    @Test
    void everyKeyedProviderAdapterIsCreated() {
        assertThat(context.getBeansOfType(FeatherlessLlmClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(GeminiLlmClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(GroqLlmClient.class)).hasSize(1);
    }
}
