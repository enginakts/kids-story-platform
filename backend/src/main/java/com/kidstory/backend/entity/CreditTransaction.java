package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "credit_transactions")
public class CreditTransaction extends CreatedEntity {

    public enum Type { WELCOME_BONUS, SUBSCRIPTION_RENEWAL, STORY_SPEND, REFUND, ADMIN_ADJUSTMENT }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private CreditWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private Type transactionType;

    @Column(name = "story_credit_amount", nullable = false)
    private int storyCreditAmount;

    @Column(name = "interactive_credit_amount", nullable = false)
    private int interactiveCreditAmount;

    private String description;
}
