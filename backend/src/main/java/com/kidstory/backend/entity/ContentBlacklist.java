package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_blacklists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", unique = true, nullable = false, length = 100)
    private String keyword;

    @Column(name = "category", nullable = false, length = 50)
    private String category; // FEAR, VIOLENCE, SEXUAL, PROFANITY

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType; // BLOCK, REPLACE, WARN

    @Column(name = "replacement_word", length = 100)
    private String replacementWord;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
