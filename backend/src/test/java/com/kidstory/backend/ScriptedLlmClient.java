package com.kidstory.backend;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.service.ai.LlmClient;
import com.kidstory.backend.service.ai.LlmRequest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/** Testlerde gerçek sağlayıcının yerine geçer (aynı sağlayıcı adı); yanıtlar sırayla verilir, istekler kaydedilir. */
public class ScriptedLlmClient implements LlmClient {

    private final String name;
    private final AiProviderConfig.Type type;
    private final Deque<Supplier<String>> answers = new ArrayDeque<>();
    public final List<LlmRequest> requests = new CopyOnWriteArrayList<>();

    public ScriptedLlmClient(String name, AiProviderConfig.Type type) {
        this.name = name;
        this.type = type;
    }

    public synchronized void willReturn(String answer) {
        answers.add(() -> answer);
    }

    public synchronized void willFail(RuntimeException e) {
        answers.add(() -> {
            throw e;
        });
    }

    public synchronized void reset() {
        answers.clear();
        requests.clear();
    }

    @Override
    public String providerName() {
        return name;
    }

    @Override
    public AiProviderConfig.Type type() {
        return type;
    }

    @Override
    public synchronized String complete(LlmRequest request, AiProviderConfig config) {
        requests.add(request);
        Supplier<String> next = answers.poll();
        if (next == null) {
            throw new IllegalStateException("Test yanıtı tanımlanmadı");
        }
        return next.get();
    }

    /** "featherless" (Trendyol, metin) ve "gemini" (sahneler) yerine geçen iki istemci. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        ScriptedLlmClient textLlm() {
            return new ScriptedLlmClient("featherless", AiProviderConfig.Type.LLM);
        }

        @Bean
        ScriptedLlmClient sceneLlm() {
            return new ScriptedLlmClient("gemini", AiProviderConfig.Type.SCENE);
        }
    }
}
