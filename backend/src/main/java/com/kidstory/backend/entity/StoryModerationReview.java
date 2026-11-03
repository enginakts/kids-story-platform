package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "story_moderation_reviews")
public class StoryModerationReview extends CreatedEntity {

    public enum Decision { APPROVED, REJECTED }

    public enum Source { AUTOMATED, ADMIN }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_source", nullable = false, length = 20)
    private Source reviewSource;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
