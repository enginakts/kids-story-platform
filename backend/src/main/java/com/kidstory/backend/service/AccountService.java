package com.kidstory.backend.service;

import com.kidstory.backend.entity.AccountToken;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.exception.InvalidTokenException;
import com.kidstory.backend.repository.AccountTokenRepository;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.security.LoginAttemptService;
import com.kidstory.backend.security.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** E-posta doğrulama ve parola sıfırlama. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final ParentRepository parents;
    private final AccountTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender email;
    private final String baseUrl;
    private final boolean verificationEnabled;

    AccountService(ParentRepository parents, AccountTokenRepository tokens, RefreshTokenService refreshTokens,
                   LoginAttemptService loginAttempts, PasswordEncoder passwordEncoder, EmailSender email,
                   @Value("${app.base-url}") String baseUrl,
                   @Value("${app.account.email-verification.enabled}") boolean verificationEnabled) {
        this.parents = parents;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.verificationEnabled = verificationEnabled;
    }

    /** Doğrulama kapalıysa arayüz uyarı göstermez ve kayıtta e-posta gönderilmez. */
    public boolean isVerificationEnabled() {
        return verificationEnabled;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRegistered(ParentRegisteredEvent event) {
        if (!verificationEnabled) {
            return;
        }
        try {
            sendVerification(event.parentId());
        } catch (RuntimeException e) {
            // Kayıt geri alınmaz; ebeveyn bağlantıyı panelden yeniden isteyebilir.
            log.warn("Doğrulama e-postası gönderilemedi (parentId={}): {}", event.parentId(), e.toString());
        }
    }

    @Transactional
    public void sendVerification(Long parentId) {
        Parent parent = parents.findById(parentId).orElseThrow();
        if (parent.isEmailVerified()) {
            return;
        }
        String raw = issue(parent, AccountToken.Type.EMAIL_VERIFICATION, VERIFY_TTL);
        email.send(parent.getEmail(), "Masal Dünyası: e-posta adresinizi doğrulayın",
                "Merhaba " + parent.getFirstName() + ",\n\nHesabınızı doğrulamak için bağlantıyı açın:\n"
                        + baseUrl + "/verify-email?token=" + raw + "\n\nBağlantı 24 saat geçerlidir.");
    }

    @Transactional
    public void verifyEmail(String raw) {
        AccountToken token = consume(raw, AccountToken.Type.EMAIL_VERIFICATION);
        token.getParent().setEmailVerified(true);
    }

    /** E-postanın kayıtlı olup olmadığı dışarıya belli edilmez. */
    @Transactional
    public void requestPasswordReset(String emailAddress) {
        parents.findByEmail(emailAddress.trim().toLowerCase(Locale.ROOT))
                .filter(Parent::isActive)
                .ifPresent(parent -> {
                    String raw = issue(parent, AccountToken.Type.PASSWORD_RESET, RESET_TTL);
                    email.send(parent.getEmail(), "Masal Dünyası: parola sıfırlama",
                            "Parolanızı sıfırlamak için bağlantıyı açın:\n" + baseUrl + "/reset-password?token=" + raw
                                    + "\n\nBağlantı 1 saat geçerlidir. Bu isteği siz yapmadıysanız e-postayı yok sayın.");
                });
    }

    /** Başarılı sıfırlamada mobil oturumlar kapatılır ve giriş kilidi kaldırılır. */
    @Transactional
    public void resetPassword(String raw, String newPassword) {
        Parent parent = consume(raw, AccountToken.Type.PASSWORD_RESET).getParent();
        parent.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAll(parent.getId());
        loginAttempts.reset("parent", parent.getEmail());
    }

    @Transactional(readOnly = true)
    public boolean isResetTokenUsable(String raw) {
        return raw != null && tokens.findByTokenHashAndTokenType(SecureTokens.hash(raw), AccountToken.Type.PASSWORD_RESET)
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    private String issue(Parent parent, AccountToken.Type type, Duration ttl) {
        tokens.invalidateOpen(parent.getId(), type, Instant.now());
        String raw = SecureTokens.generate();
        AccountToken token = new AccountToken();
        token.setParent(parent);
        token.setTokenType(type);
        token.setTokenHash(SecureTokens.hash(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        tokens.save(token);
        return raw;
    }

    private AccountToken consume(String raw, AccountToken.Type type) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidTokenException();
        }
        AccountToken token = tokens.findByTokenHashAndTokenType(SecureTokens.hash(raw), type)
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidTokenException::new);
        token.setUsedAt(Instant.now());
        return token;
    }
}
