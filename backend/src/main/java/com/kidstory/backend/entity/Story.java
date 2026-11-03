package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity @Table(name = "stories")
public class Story extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private ChildProfile child;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_character_id")
    private CharacterBible primaryCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_location_id")
    private LocationBible primaryLocation;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StoryEnums.Theme theme;

    @Enumerated(EnumType.STRING)
    @Column(name = "narration_style", nullable = false, length = 50)
    private StoryEnums.NarrationStyle narrationStyle = StoryEnums.NarrationStyle.WARM;

    @Column(name = "target_duration_minutes", nullable = false)
    private int targetDurationMinutes = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "story_mode", nullable = false, length = 50)
    private StoryEnums.Mode storyMode = StoryEnums.Mode.SINGLE_SHOT;

    @Column(name = "personalized_used", nullable = false)
    private boolean personalizedUsed;

    @Column(name = "total_scenes", nullable = false)
    private int totalScenes = 4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StoryEnums.Status status = StoryEnums.Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoryEnums.Visibility visibility = StoryEnums.Visibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private StoryEnums.ModerationStatus moderationStatus = StoryEnums.ModerationStatus.NOT_REQUESTED;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "storage_expiry_date")
    private Instant storageExpiryDate;
}
