package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Sağlayıcı önceliği yönetimden değişir. API anahtarları yalnızca ortam değişkenindedir. */
@Getter @Setter
@Entity @Table(name = "ai_providers_config")
public class AiProviderConfig extends IdEntity {

    public enum Type { LLM, IMAGE, TTS }

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 50)
    private Type providerType;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "active_model_name", nullable = false, length = 150)
    private String activeModelName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int priority = 1;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 15000;

    @Column(name = "cost_per_1k_tokens", nullable = false, precision = 8, scale = 6)
    private BigDecimal costPer1kTokens = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
