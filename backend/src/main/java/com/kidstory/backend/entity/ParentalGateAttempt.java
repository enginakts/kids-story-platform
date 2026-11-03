package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "parental_gate_attempts")
public class ParentalGateAttempt extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Column(name = "challenge_question", nullable = false, length = 100)
    private String challengeQuestion;

    @Column(name = "is_success", nullable = false)
    private boolean success;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
