package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter @Setter
@Entity @Table(name = "credit_wallets")
public class CreditWallet extends IdEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false, unique = true)
    private Parent parent;

    @Column(name = "story_credit_balance", nullable = false)
    private int storyCreditBalance = 3;

    @Column(name = "interactive_credit_balance", nullable = false)
    private int interactiveCreditBalance = 1;

    /** Eşzamanlı isteklerde aynı kredinin iki kez harcanmasını engeller. */
    @Version
    @Column(nullable = false)
    private long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
