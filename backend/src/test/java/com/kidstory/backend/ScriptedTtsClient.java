package com.kidstory.backend;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.service.ai.KikiriTtsClient;
import com.kidstory.backend.service.ai.TtsClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Testlerde Kikiri ses servisinin yerine geçer; istekleri kaydeder, istenirse hata verir. */
public class ScriptedTtsClient implements TtsClient {

    public final List<SpeechRequest> requests = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    public void failWith(RuntimeException e) {
        failure = e;
    }

    public void reset() {
        requests.clear();
        failure = null;
    }

    @Override
    public String providerName() {
        return KikiriTtsClient.NAME;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.TTS;
    }

    @Override
    public SpeechResult synthesize(SpeechRequest request, AiProviderConfig config) {
        requests.add(request);
        if (failure != null) {
            throw failure;
        }
        return new SpeechResult(("ID3-ses:" + request.text()).getBytes(StandardCharsets.UTF_8), "audio/mpeg", new BigDecimal("3.25"), null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        ScriptedTtsClient scriptedTts() {
            return new ScriptedTtsClient();
        }
    }
}
