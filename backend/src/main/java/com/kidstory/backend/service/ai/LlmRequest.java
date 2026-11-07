package com.kidstory.backend.service.ai;

import java.util.Map;

/** Sağlayıcıdan bağımsız LLM isteği. schema, yanıtın uyması gereken JSON şemasıdır. */
public record LlmRequest(String systemPrompt, String userPrompt, String schemaName, Map<String, Object> schema) {
}
