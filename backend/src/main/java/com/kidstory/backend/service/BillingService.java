package com.kidstory.backend.service;

import com.kidstory.backend.entity.CreditTransaction;
import com.kidstory.backend.entity.CreditWallet;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.ParentSubscription;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.SubscriptionPlan;
import com.kidstory.backend.exception.InsufficientCreditsException;
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

        record(wallet, null, CreditTransaction.Type.WELCOME_BONUS, free.getMonthlyStoryCredits(),
                free.getInteractiveCredits(), "Hoş geldin kredileri");
    }

    @Transactional(readOnly = true)
    public Optional<CreditWallet> wallet(Long parentId) {
        return wallets.findByParentId(parentId);
    }

    /**
     * Masal için 1 kredi harcar. Cüzdan @Version ile korunur: aynı anda iki istek gelirse
     * biri OptimisticLockingFailureException alır ve kredi iki kez harcanmaz.
     */
    @Transactional
    public void spendStoryCredit(Long parentId, Story story) {
        CreditWallet wallet = wallets.findByParentId(parentId).orElseThrow(InsufficientCreditsException::new);
        if (wallet.getStoryCreditBalance() < 1) {
            throw new InsufficientCreditsException();
        }
        wallet.setStoryCreditBalance(wallet.getStoryCreditBalance() - 1);
        record(wallet, story, CreditTransaction.Type.STORY_SPEND, -1, 0, "Masal oluşturma");
    }

    /** Üretim kalıcı olarak başarısız olursa harcanan kredi iade edilir. */
    @Transactional
    public void refundStoryCredit(Long parentId, Story story) {
        CreditWallet wallet = wallets.findByParentId(parentId).orElseThrow();
        wallet.setStoryCreditBalance(wallet.getStoryCreditBalance() + 1);
        record(wallet, story, CreditTransaction.Type.REFUND, 1, 0, "Masal oluşturulamadı, kredi iadesi");
    }

    private void record(CreditWallet wallet, Story story, CreditTransaction.Type type, int storyAmount,
                        int interactiveAmount, String description) {
        CreditTransaction transaction = new CreditTransaction();
        transaction.setWallet(wallet);
        transaction.setStory(story);
        transaction.setTransactionType(type);
        transaction.setStoryCreditAmount(storyAmount);
        transaction.setInteractiveCreditAmount(interactiveAmount);
        transaction.setDescription(description);
        transactions.save(transaction);
    }
}
