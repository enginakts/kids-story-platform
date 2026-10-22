package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private CreditWallet wallet;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType; // USAGE, PURCHASE, REFUND, BONUS

    @Column(name = "story_credit_amount", nullable = false)
    private Integer storyCreditAmount;

    @Column(name = "interactive_credit_amount", nullable = false)
    private Integer interactiveCreditAmount;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
