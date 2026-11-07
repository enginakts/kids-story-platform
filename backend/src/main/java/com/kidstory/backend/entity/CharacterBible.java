package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "character_bibles")
public class CharacterBible extends CreatedEntity {

    /** Boşsa herkesin kullanabildiği sistem şablonudur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @Column(nullable = false, length = 100)
    private String name;

    /** Türkçe tür adı (ör. "Tavşan"); arayüzde gösterilir. */
    @Column(nullable = false, length = 50)
    private String species;

    @Column(name = "species_code", length = 30)
    private String speciesCode;

    @Column(name = "color_code", length = 30)
    private String colorCode;

    @Column(name = "outfit_code", length = 30)
    private String outfitCode;

    @Column(name = "accessory_code", length = 30)
    private String accessoryCode;

    @Column(name = "personality_traits", columnDefinition = "TEXT")
    private String personalityTraits;

    /** Görsel üretimde her sahneye eklenen İngilizce tanım. Ebeveynden alınmaz, sistemce üretilir. */
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

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
