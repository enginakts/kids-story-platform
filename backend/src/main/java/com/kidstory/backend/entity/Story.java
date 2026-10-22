package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private ChildProfile childProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_character_id")
    private CharacterBible primaryCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_location_id")
    private LocationBible primaryLocation;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "theme", nullable = false, length = 150)
    private String theme;

    @Column(name = "story_mode", nullable = false, length = 50)
    @Builder.Default
    private String storyMode = "SINGLE_SHOT"; // SINGLE_SHOT veya INTERACTIVE

    @Column(name = "personalized_used", nullable = false)
    @Builder.Default
    private Boolean personalizedUsed = false;

    @Column(name = "total_scenes", nullable = false)
    @Builder.Default
    private Integer totalScenes = 4;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING"; // PENDING, GENERATING, COMPLETED, FAILED

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "storage_expiry_date")
    private LocalDateTime storageExpiryDate;

    @Column(name = "is_favorite", nullable = false)
    @Builder.Default
    private Boolean isFavorite = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
