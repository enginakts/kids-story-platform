package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;


/** Her sağlayıcı adaptörünün ortak sözleşmesi. Ad, ai_providers_config.provider_name ile eşleşir. */
public interface AiClient {

    String providerName();

    AiProviderConfig.Type type();

    /** En az bir açık API anahtarı var mı (anahtarsız adaptörlerde her zaman evet). */
    default boolean hasUsableKey() {
        return true;
    }
}
