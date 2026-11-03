package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;

@Getter @Setter
@Entity @Table(name = "child_profiles")
public class ChildProfile extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer age;

    @Column(length = 20)
    private String gender;

    @Column(name = "favorite_animals", columnDefinition = "TEXT")
    private String favoriteAnimals;

    @Column(name = "favorite_colors", columnDefinition = "TEXT")
    private String favoriteColors;

    @Column(name = "special_interests", columnDefinition = "TEXT")
    private String specialInterests;

    @Column(name = "bedtime_hour", nullable = false)
    private LocalTime bedtimeHour = LocalTime.of(20, 30);

    @Column(name = "avatar_key", nullable = false, length = 50)
    private String avatarKey = "default";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
