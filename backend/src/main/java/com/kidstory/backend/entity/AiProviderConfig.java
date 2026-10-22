package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_providers_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType; // LLM, IMAGE, TTS

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName; // Gemini, Groq, Cloudflare AI, ElevenLabs

    @Column(name = "active_model_name", nullable = false, length = 150)
    private String activeModelName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1; // 1: Birincil, 2: Yedek

    @Column(name = "timeout_ms", nullable = false)
    @Builder.Default
    private Integer timeoutMs = 15000;

    @Column(name = "cost_per_1k_tokens", precision = 8, scale = 6)
    @Builder.Default
    private BigDecimal costPer1kTokens = BigDecimal.ZERO;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
