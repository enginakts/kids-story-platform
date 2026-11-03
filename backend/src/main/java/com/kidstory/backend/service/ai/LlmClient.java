package com.kidstory.backend.service.ai;

public interface LlmClient extends AiClient {

    /** Modelin ham JSON yanıtını döner; şema ve güvenlik denetimi çağıranda yapılır. */
    String complete(String systemPrompt, String userPrompt, int timeoutMs);
}
