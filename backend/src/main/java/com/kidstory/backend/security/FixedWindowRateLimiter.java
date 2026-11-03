package com.kidstory.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bellek içi sabit pencereli sayaç. Tek sunucu için yeterlidir;
 * birden fazla sunucuya geçildiğinde paylaşımlı bir depoya taşınmalıdır.
 */
public class FixedWindowRateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Sayacı artırır; sınır aşılmışsa false döner. */
    public boolean tryAcquire(String key) {
        return increment(key) <= limit;
    }

    /** Sayacı artırmadan sınırın aşılıp aşılmadığını söyler. */
    public boolean isBlocked(String key) {
        Window w = windows.get(key);
        return w != null && !w.expired(clock.millis()) && w.count >= limit;
    }

    public int increment(String key) {
        long now = clock.millis();
        if (windows.size() > MAX_KEYS) {
            windows.values().removeIf(w -> w.expired(now));
        }
        return windows.compute(key, (k, w) -> w == null || w.expired(now) ? new Window(now + windowMillis, 1) : w.next()).count;
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private record Window(long resetAt, int count) {
        boolean expired(long now) {
            return now >= resetAt;
        }

        Window next() {
            return new Window(resetAt, count + 1);
        }
    }
}
