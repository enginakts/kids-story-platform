package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "location_bibles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationBible {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "environment_type", nullable = false, length = 100)
    private String environmentType;

    @Column(name = "atmosphere_description", columnDefinition = "TEXT", nullable = false)
    private String atmosphereDescription;

    @Column(name = "lighting_style", nullable = false, length = 100)
    private String lightingStyle;

    @Column(name = "recurring_landmarks", columnDefinition = "TEXT")
    private String recurringLandmarks;

    @Column(name = "visual_anchor_tokens", columnDefinition = "TEXT", nullable = false)
    private String visualAnchorTokens;

    @Column(name = "reference_image_url", length = 500)
    private String referenceImageUrl;

    @Column(name = "base_seed")
    private Long baseSeed;

    @Column(name = "is_system_preset", nullable = false)
    @Builder.Default
    private Boolean isSystemPreset = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
