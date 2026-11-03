package com.kidstory.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/** Aynı hesaba art arda hatalı girişte hesabı geçici olarak kilitler (parola deneme saldırısına karşı). */
@Service
public class LoginAttemptService {

    private final FixedWindowRateLimiter failures;

    LoginAttemptService(@Value("${app.security.login-max-failures}") int maxFailures,
                        @Value("${app.security.login-lock-minutes}") int lockMinutes) {
        this.failures = new FixedWindowRateLimiter(maxFailures, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
    }

    public boolean isLocked(String realm, String login) {
        return failures.isBlocked(key(realm, login));
    }

    public void recordFailure(String realm, String login) {
        failures.increment(key(realm, login));
    }

    public void reset(String realm, String login) {
        failures.reset(key(realm, login));
    }

    private static String key(String realm, String login) {
        return realm + ":" + login.trim().toLowerCase(Locale.ROOT);
    }
}
