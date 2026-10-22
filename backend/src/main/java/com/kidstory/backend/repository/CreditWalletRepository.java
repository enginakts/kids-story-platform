package com.kidstory.backend.repository;

import com.kidstory.backend.entity.CreditWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditWalletRepository extends JpaRepository<CreditWallet, Long> {
    Optional<CreditWallet> findByParentId(Long parentId);
}
