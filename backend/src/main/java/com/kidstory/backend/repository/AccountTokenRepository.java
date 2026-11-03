package com.kidstory.backend.repository;

import com.kidstory.backend.entity.AccountToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    Optional<AccountToken> findByTokenHashAndTokenType(String tokenHash, AccountToken.Type tokenType);

    /** Yeni bağlantı gönderildiğinde eski kullanılmamış bağlantılar geçersiz olur. */
    @Modifying
    @Query("update AccountToken t set t.usedAt = :now where t.parent.id = :parentId and t.tokenType = :type and t.usedAt is null")
    int invalidateOpen(@Param("parentId") Long parentId, @Param("type") AccountToken.Type type, @Param("now") Instant now);
}
