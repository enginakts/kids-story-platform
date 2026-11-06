package com.kidstory.backend.service;

import com.kidstory.backend.entity.CreditTransaction;
import com.kidstory.backend.entity.CreditWallet;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.ParentSubscription;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.exception.ConflictException;
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

    /**
     * Masal başına bir kredi düşer. Cüzdandaki @Version, aynı kredinin eşzamanlı iki istekte
     * harcanmasını engeller; veritabanı kısıtı da bakiyenin eksiye düşmesine izin vermez.
     */
    @Transactional
    public void spendStoryCredit(Long parentId, Story story) {
        CreditWallet wallet = wallets.findByParentId(parentId).orElseThrow();
        if (wallet.getStoryCreditBalance() < 1) {
            throw new ConflictException("Masal krediniz kalmadı");
        }
        wallet.setStoryCreditBalance(wallet.getStoryCreditBalance() - 1);
        record(wallet, story, CreditTransaction.Type.STORY_SPEND, -1, "Masal oluşturma");
    }

    /** Üretim kalıcı olarak başarısız olursa harcanan kredi geri verilir. */
    @Transactional
    public void refundStoryCredit(Story story) {
        CreditWallet wallet = wallets.findByParentId(story.getParent().getId()).orElseThrow();
        wallet.setStoryCreditBalance(wallet.getStoryCreditBalance() + 1);
        record(wallet, story, CreditTransaction.Type.REFUND, 1, "Masal üretilemedi, kredi iade edildi");
    }

    private void record(CreditWallet wallet, Story story, CreditTransaction.Type type, int amount, String description) {
        CreditTransaction transaction = new CreditTransaction();
        transaction.setWallet(wallet);
        transaction.setStory(story);
        transaction.setTransactionType(type);
        transaction.setStoryCreditAmount(amount);
        transaction.setInteractiveCreditAmount(0);
        transaction.setDescription(description);
        transactions.save(transaction);
    }

    @Transactional(readOnly = true)
    public Optional<CreditWallet> wallet(Long parentId) {
        return wallets.findByParentId(parentId);
    }
}
