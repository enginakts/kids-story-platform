package com.kidstory.backend.service.ai;

import lombok.Getter;

/** Sağlayıcı hız sınırı (HTTP 429). İş, retryAfterSeconds sonra yeniden denenir. */
@Getter
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitedException(String provider, long retryAfterSeconds) {
        super(provider + " hız sınırına ulaşıldı");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
