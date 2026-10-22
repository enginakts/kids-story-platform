package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "character_bibles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterBible {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private ChildProfile childProfile;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "species", nullable = false, length = 50)
    private String species;

    @Column(name = "personality_traits", columnDefinition = "TEXT")
    private String personalityTraits;

    @Column(name = "visual_anchor_tokens", columnDefinition = "TEXT", nullable = false)
    private String visualAnchorTokens;

    @Column(name = "clothing_description", columnDefinition = "TEXT")
    private String clothingDescription;

    @Column(name = "voice_id", length = 100)
    private String voiceId;

    @Column(name = "base_seed")
    private Long baseSeed;

    @Column(name = "reference_image_url", length = 500)
    private String referenceImageUrl;

    @Column(name = "is_system_preset", nullable = false)
    @Builder.Default
    private Boolean isSystemPreset = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
