package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;

public interface LlmClient extends AiClient {

    /**
     * Şemaya uygun ham JSON metnini döner. Model ve zaman aşımı yönetimdeki sağlayıcı satırından (config) gelir.
     * İçerik güvenliği ve şema doğrulaması çağıranda yapılır.
     */
    String complete(LlmRequest request, AiProviderConfig config);
}
