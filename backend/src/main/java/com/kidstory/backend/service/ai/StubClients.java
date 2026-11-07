package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dış servis ve API anahtarı olmadan geliştirme taslakları. Üretimde yüklenmez.
 * Bir rolün gerçek anahtarı varsa o rolün taslağı yüklenmez: gerçek sağlayıcı hata verirse
 * sessizce taslağa düşülmesin, iş yeniden denensin.
 */
@Configuration
@Profile("!prod")
class StubClients {

    @Bean
    @ConditionalOnExpression("'${app.ai.featherless.api-key:}' == ''")
    LlmClient stubStoryText() {
        return new Stub("stub", AiProviderConfig.Type.LLM, """
                Paylaşınca Çoğalan Mutluluk

                Güneşli bir sabah ormanda küçük bir sepet dolusu çilek bulundu.

                Dostlar sepetin etrafında toplandı ve gülümsedi.

                Herkes çilekleri sırayla ve eşit paylaştı.

                Akşam olunca karınları tok, kalpleri sevinçle doluydu.""");
    }

    @Bean
    @ConditionalOnExpression("'${app.ai.gemini.api-key:}' == ''")
    LlmClient stubScenes() {
        return new Stub("stub-scene", AiProviderConfig.Type.SCENE, """
                {"title":"Paylaşınca Çoğalan Mutluluk","scenes":[
                {"text":"Güneşli bir sabah ormanda küçük bir sepet dolusu çilek bulundu.","image_prompt":"a basket of strawberries on soft grass in a sunny forest"},
                {"text":"Dostlar sepetin etrafında toplandı ve gülümsedi.","image_prompt":"friendly animals gathering around a basket of strawberries, smiling"},
                {"text":"Herkes çilekleri sırayla ve eşit paylaştı.","image_prompt":"animals sharing strawberries one by one, happy faces"},
                {"text":"Akşam olunca karınları tok, kalpleri sevinçle doluydu.","image_prompt":"animals resting together under a pastel sunset sky"}]}""");
    }

    @Bean
    ImageClient stubImage() {
        return new ImageClient() {
            @Override
            public String providerName() {
                return "stub";
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
                return "stub";
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

    private record Stub(String providerName, AiProviderConfig.Type type, String answer) implements LlmClient {

        @Override
        public String complete(LlmRequest request, AiProviderConfig config) {
            return answer;
        }
    }
}
