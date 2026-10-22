package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "child_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChildProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "favorite_animals", columnDefinition = "TEXT")
    private String favoriteAnimals;

    @Column(name = "favorite_colors", columnDefinition = "TEXT")
    private String favoriteColors;

    @Column(name = "special_interests", columnDefinition = "TEXT")
    private String specialInterests;

    @Column(name = "bedtime_hour", length = 10)
    @Builder.Default
    private String bedtimeHour = "20:30:00";

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
