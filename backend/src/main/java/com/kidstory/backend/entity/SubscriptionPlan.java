package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity @Table(name = "subscription_plans")
public class SubscriptionPlan extends IdEntity {

    public static final String FREE = "FREE_TIER";

    @Column(name = "plan_code", nullable = false, unique = true, length = 50)
    private String planCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "monthly_story_credits", nullable = false)
    private int monthlyStoryCredits;

    @Column(name = "interactive_credits", nullable = false)
    private int interactiveCredits;

    @Column(name = "max_child_profiles", nullable = false)
    private int maxChildProfiles = 1;

    @Column(name = "storage_retention_days", nullable = false)
    private int storageRetentionDays;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
