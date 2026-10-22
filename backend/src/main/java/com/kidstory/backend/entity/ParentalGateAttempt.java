package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "parental_gate_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentalGateAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Column(name = "challenge_question", nullable = false, length = 100)
    private String challengeQuestion;

    @Column(name = "is_success", nullable = false)
    private Boolean isSuccess;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType; // SETTINGS_ACCESS, CREDIT_PURCHASE, PROFILE_EDIT

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
