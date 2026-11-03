package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Dış servis ve API anahtarı olmadan geliştirme taslakları. Üretimde yüklenmez. */
@Configuration
@Profile("!prod")
class StubClients {

    private static final String NAME = "stub";

    @Bean
    LlmClient stubLlm() {
        return new LlmClient() {
            @Override
            public String providerName() {
                return NAME;
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.LLM;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, int timeoutMs) {
                return "{\"title\":\"Taslak Masal\",\"scenes\":[]}";
            }
        };
    }

    @Bean
    ImageClient stubImage() {
        return new ImageClient() {
            @Override
            public String providerName() {
                return NAME;
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.IMAGE;
            }

            @Override
            public byte[] generate(String prompt, String negativePrompt, Long seed, int timeoutMs) {
                return new byte[0];
            }
        };
    }

    @Bean
    TtsClient stubTts() {
        return new TtsClient() {
            @Override
            public String providerName() {
                return NAME;
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.TTS;
            }

            @Override
            public SpeechResult synthesize(String text, String voiceId, int timeoutMs) {
                return new SpeechResult(new byte[0], "[]");
            }
        };
    }
}
