package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "location_bibles")
public class LocationBible extends CreatedEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "environment_type", nullable = false, length = 100)
    private String environmentType;

    @Column(name = "atmosphere_description", nullable = false, columnDefinition = "TEXT")
    private String atmosphereDescription;

    @Column(name = "lighting_style", nullable = false, length = 100)
    private String lightingStyle;

    @Column(name = "recurring_landmarks", columnDefinition = "TEXT")
    private String recurringLandmarks;

    @Column(name = "visual_anchor_tokens", nullable = false, columnDefinition = "TEXT")
    private String visualAnchorTokens;

    @Column(name = "reference_image_url", length = 500)
    private String referenceImageUrl;

    @Column(name = "base_seed")
    private Long baseSeed;

    @Column(name = "is_system_preset", nullable = false)
    private boolean systemPreset;
}
