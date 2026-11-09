package com.kidstory.backend.service.ai;

import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Groq adaptörü (V9 itibarıyla masal metninin ana sağlayıcısı). Şema verilirse yanıt json_schema strict modu ile
 * şemaya uydurulur; akıl yürütme metni istenmez. Prompt'taki Qwen'e özgü "/no_think" komutu Groq'a gönderilmez.
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
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt().replaceAll("\\s*/no_think\\s*$", ""))));
        if (request.schema() != null) {
            body.put("response_format", Map.of("type", "json_schema", "json_schema",
                    Map.of("name", request.schemaName(), "strict", true, "schema", request.schema())));
        }
    }
}
