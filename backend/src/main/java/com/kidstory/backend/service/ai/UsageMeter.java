package com.kidstory.backend.service.ai;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bir dış çağrının ölçümü (kullanım raporu, V14). Dış çağrı yapmaz; istemciler yanıtta zaten gelen bilgiyi buraya bildirir:
 * token sayıları, HTTP durumu ve (Groq gibi) sağlayıcının kalan kota başlıkları.
 * Çağrı ve iş bağlamı aynı iş parçacığında tutulur (ThreadLocal): yönlendirici her denemeden önce sıfırlar, sonra okur.
 */
public final class UsageMeter {

    /** Bir çağrıda ölçülenler; bilinmeyen alan null kalır. */
    public static final class Reading {
        Integer inputTokens;
        Integer outputTokens;
        Integer httpStatus;

        public Integer inputTokens() { return inputTokens; }
        public Integer outputTokens() { return outputTokens; }
        public Integer httpStatus() { return httpStatus; }
    }

    /** Çağrının ait olduğu masal ve üretim işi (iş çalışanı ayarlar). */
    public record Context(Long storyId, Long jobId) {
    }

    /** Sağlayıcının son yanıtta bildirdiği kota başlıkları (bellekte; uygulama yeniden başlayınca boşalır). */
    public record RateLimit(Map<String, String> headers, Instant seenAt) {

        /** Türkiye saatiyle (şablonda gösterim için). */
        public java.time.LocalDateTime seenAtLocal() {
            return java.time.LocalDateTime.ofInstant(seenAt, java.time.ZoneId.of("Europe/Istanbul"));
        }
    }

    private static final ThreadLocal<Reading> READING = new ThreadLocal<>();
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static final Map<String, RateLimit> RATE_LIMITS = new ConcurrentHashMap<>();

    private UsageMeter() {
    }

    static Reading start() {
        Reading r = new Reading();
        READING.set(r);
        return r;
    }

    static Reading finish() {
        Reading r = READING.get();
        READING.remove();
        return r == null ? new Reading() : r;
    }

    /** İstemci: yanıttaki token sayıları. */
    public static void tokens(Integer input, Integer output) {
        Reading r = READING.get();
        if (r != null) {
            r.inputTokens = input;
            r.outputTokens = output;
        }
    }

    /** İstemci: yanıtın HTTP durumu (hata durumunda da). */
    public static void status(int httpStatus) {
        Reading r = READING.get();
        if (r != null) {
            r.httpStatus = httpStatus;
        }
    }

    /** İstemci: yanıtla gelen kalan kota başlıkları (x-ratelimit-*). */
    public static void rateLimit(String provider, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            RATE_LIMITS.put(provider, new RateLimit(new LinkedHashMap<>(headers), Instant.now()));
        }
    }

    public static Map<String, RateLimit> rateLimits() {
        return Map.copyOf(RATE_LIMITS);
    }

    /** İş çalışanı: bu iş parçacığındaki çağrılar hangi masal ve iş için. */
    public static void context(Long storyId, Long jobId) {
        CONTEXT.set(new Context(storyId, jobId));
    }

    public static void clearContext() {
        CONTEXT.remove();
    }

    static Context currentContext() {
        Context c = CONTEXT.get();
        return c == null ? new Context(null, null) : c;
    }
}
