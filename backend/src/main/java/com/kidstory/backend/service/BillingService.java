package com.kidstory.backend.service;

import com.kidstory.backend.entity.CreditTransaction;
import com.kidstory.backend.entity.CreditWallet;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.ParentSubscription;
import com.kidstory.backend.entity.SubscriptionPlan;
import com.kidstory.backend.repository.CreditTransactionRepository;
import com.kidstory.backend.repository.CreditWalletRepository;
import com.kidstory.backend.repository.ParentSubscriptionRepository;
import com.kidstory.backend.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final SubscriptionPlanRepository plans;
    private final ParentSubscriptionRepository subscriptions;
    private final CreditWalletRepository wallets;
    private final CreditTransactionRepository transactions;

    /** Yeni ebeveyne ücretsiz planı, cüzdanı ve hoş geldin kredilerini tanımlar. */
    @Transactional
    public void openFreeAccount(Parent parent) {
        SubscriptionPlan free = plans.findByPlanCode(SubscriptionPlan.FREE).orElseThrow();

        ParentSubscription subscription = new ParentSubscription();
        subscription.setParent(parent);
        subscription.setPlan(free);
        subscriptions.save(subscription);

        CreditWallet wallet = new CreditWallet();
        wallet.setParent(parent);
        wallet.setStoryCreditBalance(free.getMonthlyStoryCredits());
        wallet.setInteractiveCreditBalance(free.getInteractiveCredits());
        wallets.save(wallet);

        CreditTransaction transaction = new CreditTransaction();
        transaction.setWallet(wallet);
        transaction.setTransactionType(CreditTransaction.Type.WELCOME_BONUS);
        transaction.setStoryCreditAmount(free.getMonthlyStoryCredits());
        transaction.setInteractiveCreditAmount(free.getInteractiveCredits());
        transaction.setDescription("Hoş geldin kredileri");
        transactions.save(transaction);
    }

    @Transactional(readOnly = true)
    public Optional<CreditWallet> wallet(Long parentId) {
        return wallets.findByParentId(parentId);
    }
}
