package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", unique = true, nullable = false, length = 50)
    private String planCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "monthly_story_credits", nullable = false)
    private Integer monthlyStoryCredits;

    @Column(name = "interactive_credits", nullable = false)
    private Integer interactiveCredits;

    @Column(name = "max_child_profiles", nullable = false)
    @Builder.Default
    private Integer maxChildProfiles = 1;

    @Column(name = "storage_retention_days", nullable = false)
    private Integer storageRetentionDays;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
