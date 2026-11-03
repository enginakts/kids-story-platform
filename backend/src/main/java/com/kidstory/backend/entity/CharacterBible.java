package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "character_bibles")
public class CharacterBible extends CreatedEntity {

    /** Boşsa herkesin kullanabildiği sistem şablonudur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private ChildProfile child;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String species;

    @Column(name = "personality_traits", columnDefinition = "TEXT")
    private String personalityTraits;

    @Column(name = "visual_anchor_tokens", nullable = false, columnDefinition = "TEXT")
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
    private boolean systemPreset;
}
