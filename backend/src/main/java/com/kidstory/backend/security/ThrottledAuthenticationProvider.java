package com.kidstory.backend.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;

/** Web formu, yönetici formu ve mobil API girişi aynı kilitleme kuralından geçer. */
public class ThrottledAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final LoginAttemptService attempts;
    private final String realm;

    public ThrottledAuthenticationProvider(AuthenticationProvider delegate, LoginAttemptService attempts, String realm) {
        this.delegate = delegate;
        this.attempts = attempts;
        this.realm = realm;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String login = authentication.getName();
        if (attempts.isLocked(realm, login)) {
            throw new LockedException("Çok fazla hatalı deneme");
        }
        try {
            Authentication result = delegate.authenticate(authentication);
            attempts.reset(realm, login);
            return result;
        } catch (BadCredentialsException e) {
            attempts.recordFailure(realm, login);
            throw e;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
