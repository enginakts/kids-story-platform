package com.kidstory.backend.repository;

import com.kidstory.backend.entity.CreditWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditWalletRepository extends JpaRepository<CreditWallet, Long> {

    Optional<CreditWallet> findByParentId(Long parentId);
}
