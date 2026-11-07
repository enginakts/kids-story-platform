package com.kidstory.backend.service.ai;

import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Groq adaptörü (V6 itibarıyla kapalı alternatif). Yanıt json_schema strict modu ile şemaya uydurulur;
 * akıl yürütme metni istenmez.
 */
public class GroqLlmClient extends OpenAiCompatibleLlmClient {

    public static final String NAME = "groq";

    public GroqLlmClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        super(NAME, "Groq", builder, baseUrl, apiKey);
    }

    @Override
    protected void customize(Map<String, Object> body, LlmRequest request) {
        body.put("max_completion_tokens", 4096);
        body.put("reasoning_effort", "low");
        body.put("include_reasoning", false);
        if (request.schema() != null) {
            body.put("response_format", Map.of("type", "json_schema", "json_schema",
                    Map.of("name", request.schemaName(), "strict", true, "schema", request.schema())));
        }
    }
}
