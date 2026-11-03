package com.kidstory.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTests {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };

    @Test
    void blocksAfterLimitAndResetsAfterWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        assertThat(limiter.tryAcquire("other")).isTrue();

        now.addAndGet(Duration.ofMinutes(1).toMillis());
        assertThat(limiter.tryAcquire("ip")).isTrue();
    }

    @Test
    void isBlockedDoesNotCount() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, Duration.ofMinutes(1), clock);
        limiter.increment("user");
        assertThat(limiter.isBlocked("user")).isFalse();
        assertThat(limiter.isBlocked("user")).isFalse();
        limiter.increment("user");
        assertThat(limiter.isBlocked("user")).isTrue();
        limiter.reset("user");
        assertThat(limiter.isBlocked("user")).isFalse();
    }
}
