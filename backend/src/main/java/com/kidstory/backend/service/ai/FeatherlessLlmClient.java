package com.kidstory.backend.service.ai;

import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Featherless AI üzerinden Trendyol LLM (OpenAI uyumlu). JSON şeması desteği dokümante edilmediği için
 * düz metin istenir; yapı ikinci aşamada (sahnelere bölme) kurulur. Trendyol-LLM-8B-T1 bir akıl yürütme
 * modelidir: düşünme prompt'taki /no_think ile kapatılır, yine de gelirse <think> bloğu atılır.
 */
public class FeatherlessLlmClient extends OpenAiCompatibleLlmClient {

    public static final String NAME = "featherless";
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");

    public FeatherlessLlmClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        super(NAME, "Featherless", builder, baseUrl, apiKey);
    }

    @Override
    protected void customize(Map<String, Object> body, LlmRequest request) {
        body.put("max_tokens", 2048);
    }

    @Override
    protected String postProcess(String content) {
        String withoutThinking = THINK.matcher(content).replaceAll("");
        int open = withoutThinking.indexOf("<think>");
        return (open >= 0 ? withoutThinking.substring(0, open) : withoutThinking).trim();
    }
}
