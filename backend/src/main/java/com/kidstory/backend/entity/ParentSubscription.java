package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "parent_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, EXPIRED, CANCELLED

    @Column(name = "start_date")
    @Builder.Default
    private LocalDateTime startDate = LocalDateTime.now();

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
