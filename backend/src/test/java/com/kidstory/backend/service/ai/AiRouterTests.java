package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.repository.AiProviderConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRouterTests {

    private final AiProviderConfigRepository repository = mock(AiProviderConfigRepository.class);

    @Test
    void fallsBackToNextProviderOnFailure() {
        configure("first", "second");
        AiRouter router = new AiRouter(repository, List.<AiClient>of(llm("first", null), llm("second", "ok")));
        assertThat(call(router)).isEqualTo("ok");
    }

    @Test
    void failsWhenNoProviderWorks() {
        configure("missing");
        AiRouter router = new AiRouter(repository, List.<AiClient>of(llm("first", "ok")));
        assertThatThrownBy(() -> call(router)).isInstanceOf(IllegalStateException.class);
    }

    private String call(AiRouter router) {
        return router.call(AiProviderConfig.Type.LLM, LlmClient.class,
                (client, config) -> client.complete("sistem", "kullanıcı", config.getTimeoutMs()));
    }

    private void configure(String... names) {
        List<AiProviderConfig> configs = java.util.Arrays.stream(names).map(name -> {
            AiProviderConfig config = new AiProviderConfig();
            config.setProviderName(name);
            return config;
        }).toList();
        when(repository.findByProviderTypeAndActiveTrueOrderByPriorityAsc(AiProviderConfig.Type.LLM)).thenReturn(configs);
    }

    private LlmClient llm(String name, String answer) {
        return new LlmClient() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.LLM;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, int timeoutMs) {
                if (answer == null) {
                    throw new IllegalStateException("kapalı");
                }
                return answer;
            }
        };
    }
}
