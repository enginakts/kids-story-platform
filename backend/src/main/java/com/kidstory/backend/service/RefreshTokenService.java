package com.kidstory.backend.service;

import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.RefreshToken;
import com.kidstory.backend.exception.InvalidTokenException;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.RefreshTokenRepository;
import com.kidstory.backend.security.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Duration;
import java.time.Instant;

/**
 * Mobil uygulama için uzun ömürlü yenileme token'ları. Her kullanımda yenisiyle değiştirilir;
 * iptal edilmiş bir token tekrar kullanılırsa çalındığı varsayılır ve ebeveynin tüm oturumları kapatılır.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final ParentRepository parents;
    private final Duration lifetime;

    RefreshTokenService(RefreshTokenRepository tokens, ParentRepository parents,
                        @Value("${app.refresh-token.days}") int days) {
        this.tokens = tokens;
        this.parents = parents;
        this.lifetime = Duration.ofDays(days);
    }

    /** İşlem dışına yalnızca düz değerler çıkar; lazy ilişkilere erişilmez. */
    public record Rotation(Long parentId, String role, String refreshToken) {
    }

    @Transactional
    public String issue(Long parentId) {
        return create(parents.getReferenceById(parentId));
    }

    /** İptal sonrası yeniden kullanım tespitinin kalıcı olması için ayrı işlemde çalışır. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = InvalidTokenException.class)
    public Rotation rotate(String raw) {
        RefreshToken current = find(raw);
        Parent parent = current.getParent();
        if (current.getRevokedAt() != null) {
            tokens.revokeAll(parent.getId(), Instant.now());
            throw new InvalidTokenException();
        }
        if (!current.getExpiresAt().isAfter(Instant.now()) || !parent.isActive()) {
            throw new InvalidTokenException();
        }
        current.setRevokedAt(Instant.now());
        return new Rotation(parent.getId(), parent.getRole().getName(), create(parent));
    }

    @Transactional
    public void revoke(String raw) {
        tokens.findByTokenHash(SecureTokens.hash(raw == null ? "" : raw))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void revokeAll(Long parentId) {
        tokens.revokeAll(parentId, Instant.now());
    }

    private RefreshToken find(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidTokenException();
        }
        return tokens.findByTokenHash(SecureTokens.hash(raw)).orElseThrow(InvalidTokenException::new);
    }

    private String create(Parent parent) {
        String raw = SecureTokens.generate();
        RefreshToken token = new RefreshToken();
        token.setParent(parent);
        token.setTokenHash(SecureTokens.hash(raw));
        token.setExpiresAt(Instant.now().plus(lifetime));
        tokens.save(token);
        return raw;
    }
}
