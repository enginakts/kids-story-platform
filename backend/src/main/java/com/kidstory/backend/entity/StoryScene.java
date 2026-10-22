package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "story_scenes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"story_id", "scene_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryScene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private LocationBible location;

    @Column(name = "scene_number", nullable = false)
    private Integer sceneNumber;

    @Column(name = "scene_text", columnDefinition = "TEXT", nullable = false)
    private String sceneText;

    @Column(name = "voice_text", columnDefinition = "TEXT", nullable = false)
    private String voiceText;

    @Column(name = "image_prompt", columnDefinition = "TEXT", nullable = false)
    private String imagePrompt;

    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    @Builder.Default
    private String negativePrompt = "no text, no watermark, no dark colors, no horror";

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "audio_duration_seconds", precision = 6, scale = 2)
    private BigDecimal audioDurationSeconds;

    @Column(name = "timestamps_json", columnDefinition = "TEXT")
    private String timestampsJson;

    @Column(name = "generation_seed")
    private Long generationSeed;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
