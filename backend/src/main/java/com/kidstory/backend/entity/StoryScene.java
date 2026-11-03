package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Getter @Setter
@Entity @Table(name = "story_scenes")
public class StoryScene extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private LocationBible location;

    @Column(name = "scene_number", nullable = false)
    private int sceneNumber;

    @Column(name = "scene_text", nullable = false, columnDefinition = "TEXT")
    private String sceneText;

    @Column(name = "voice_text", nullable = false, columnDefinition = "TEXT")
    private String voiceText;

    @Column(name = "image_prompt", nullable = false, columnDefinition = "TEXT")
    private String imagePrompt;

    @Column(name = "negative_prompt", nullable = false, columnDefinition = "TEXT")
    private String negativePrompt = "no text, no watermark, no dark colors, no horror";

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "audio_duration_seconds", precision = 6, scale = 2)
    private BigDecimal audioDurationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timestamps_json", columnDefinition = "jsonb")
    private String timestampsJson;

    @Column(name = "generation_seed")
    private Long generationSeed;
}
