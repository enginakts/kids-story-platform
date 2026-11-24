package com.kidstory.backend.service.ai;

import lombok.Getter;

/**
 * Sağlayıcı hız/kota sınırı (HTTP 429) ya da yoğunluk (HTTP 503). İş, retryAfterSeconds sonra yeniden denenir.
 * 429'da anahtar halkası o anahtarı otomatik kapatıp yedeğe geçer; 503 anahtarla ilgili değildir, kapatılmaz.
 */
@Getter
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;
    private final int status;

    public RateLimitedException(String provider, long retryAfterSeconds) {
        this(provider, retryAfterSeconds, 429);
    }

    public RateLimitedException(String provider, long retryAfterSeconds, int status) {
        super(provider + (status == 503 ? " şu an yoğun" : " hız sınırına ulaşıldı"));
        this.retryAfterSeconds = retryAfterSeconds;
        this.status = status;
    }
}
