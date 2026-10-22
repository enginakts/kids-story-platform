package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", unique = true, nullable = false)
    private Parent parent;

    @Column(name = "story_credit_balance", nullable = false)
    @Builder.Default
    private Integer storyCreditBalance = 3;

    @Column(name = "interactive_credit_balance", nullable = false)
    @Builder.Default
    private Integer interactiveCreditBalance = 1;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
