package com.kidstory.backend.service;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.repository.AiProviderConfigRepository;
import com.kidstory.backend.service.ai.AiClient;
import com.kidstory.backend.service.ai.TtsClient;
import com.kidstory.backend.service.ai.UsageMeter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seslendirme sağlayıcısını masal başına seçer (V21). Tek bir sağlayıcıya yüklenmek yerine masallar sırayla
 * farklı sağlayıcılara dağıtılır; kotası dolmuş, kısa süre önce kota hatası vermiş ya da limitine yaklaşmış
 * sağlayıcı sona alınır. Bir masalın bütün sahneleri aynı sağlayıcıyla (aynı sesle) okunur: seçim stories.tts_provider
 * sütununa yazılır. Seçilen sağlayıcı o an hata verirse yönlendirici aynı denemede sıradakine geçer.
 *
 * Sıralama ölçütleri (öncelik sırasıyla):
 *  1. Kullanılamaz (sona): yönetimden girilen günlük/aylık limit dolmuş, son 24 saatte "kota bitti" (HTTP 402) ya da
 *     son 15 dakikada "çok istek / yoğun" (HTTP 429/503) yanıtı alınmış.
 *  2. Kotaya yakın (sona doğru): limitin %80'i kullanılmış ya da sağlayıcının bildirdiği kalan karakter azalmış (Murf).
 *  3. Sıra (dağıtım): en uzun süredir masal atanmamış sağlayıcı önce. Böylece ardışık masallar farklı sağlayıcıya düşer.
 *  4. Eşitlikte yönetimdeki öncelik.
 */
@Service
@RequiredArgsConstructor
public class TtsPlanner {

    private static final Logger log = LoggerFactory.getLogger(TtsPlanner.class);
    static final Duration BUSY_COOLDOWN = Duration.ofMinutes(15);
    static final Duration QUOTA_COOLDOWN = Duration.ofHours(24);
    static final double NEAR_LIMIT = 0.8;
    /** Sağlayıcının bildirdiği kalan karakter bunun altındaysa "kotaya yakın" sayılır (yaklaşık bir masal). */
    static final long LOW_CHARACTERS = 5_000;

    private static final String TZ = "Europe/Istanbul";
    private static final String TODAY = "(date_trunc('day', now() AT TIME ZONE '" + TZ + "') AT TIME ZONE '" + TZ + "')";
    private static final String MONTH = "(date_trunc('month', now() AT TIME ZONE '" + TZ + "') AT TIME ZONE '" + TZ + "')";

    private final JdbcTemplate jdbc;
    private final AiProviderConfigRepository configs;
    private final List<AiClient> clients;

    /** Bir sağlayıcının o anki durumu (yönetim ekranı ve log için de okunaklı). */
    public record Health(String name, int priority, long todayCalls, long monthCalls, Integer dailyLimit, Integer monthlyLimit,
                         Long remainingCharacters, Instant blockedUntil, long lastStoryId) {

        boolean blocked(Instant now) {
            return blockedUntil != null && blockedUntil.isAfter(now)
                    || dailyLimit != null && todayCalls >= dailyLimit
                    || monthlyLimit != null && monthCalls >= monthlyLimit;
        }

        /** Uzun süreli engel: limit dolmuş ya da "kota bitti" (402). Kısa "meşgul" (429/503) bekleyişi sayılmaz. */
        boolean outOfQuota(Instant now) {
            return dailyLimit != null && todayCalls >= dailyLimit
                    || monthlyLimit != null && monthCalls >= monthlyLimit
                    || blockedUntil != null && blockedUntil.isAfter(now.plus(BUSY_COOLDOWN));
        }

        boolean nearLimit() {
            return dailyLimit != null && todayCalls >= dailyLimit * NEAR_LIMIT
                    || monthlyLimit != null && monthCalls >= monthlyLimit * NEAR_LIMIT
                    || remainingCharacters != null && remainingCharacters < LOW_CHARACTERS;
        }
    }

    /**
     * Masalın sağlayıcısını döndürür; henüz seçilmediyse seçip kaydeder. Seçilmiş sağlayıcı kullanılamaz durumdaysa
     * ve masalın hiç sesi yoksa yeniden seçilir (yarım kalmış masalda ses değişmesin diye sesi olan masalda değişmez).
     */
    public String assign(long storyId) {
        List<Health> ranked = rank();
        if (ranked.isEmpty()) {
            return null;
        }
        String current = jdbc.queryForObject("SELECT tts_provider FROM stories WHERE id = ?", String.class, storyId);
        Instant now = Instant.now();
        if (current != null) {
            Health h = ranked.stream().filter(x -> x.name().equals(current)).findFirst().orElse(null);
            boolean hasAudio = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM story_scenes WHERE story_id = ? AND audio_url IS NOT NULL)", Boolean.class, storyId));
            if (h != null && (!h.blocked(now) || hasAudio)) {
                return current;
            }
        }
        String chosen = ranked.getFirst().name();
        // Aynı masalın iki sahne işi aynı anda seçim yaparsa ilk yazan kazanır
        int updated = current == null
                ? jdbc.update("UPDATE stories SET tts_provider = ? WHERE id = ? AND tts_provider IS NULL", chosen, storyId)
                : jdbc.update("UPDATE stories SET tts_provider = ? WHERE id = ? AND tts_provider = ?", chosen, storyId, current);
        if (updated == 0) {
            return jdbc.queryForObject("SELECT tts_provider FROM stories WHERE id = ?", String.class, storyId);
        }
        log.info("Masal {} seslendirmesi: {} (sıra: {})", storyId, chosen,
                ranked.stream().map(this::describe).collect(Collectors.joining(", ")));
        return chosen;
    }

    /**
     * Denenecek sağlayıcılar. Masalın sesi tek olsun diye normalde yalnızca masalın sağlayıcısı denenir: o an meşgulse
     * (429) iş biraz sonra yeniden denenir. Diğer sağlayıcılara yalnızca masalın sağlayıcısının kotası bitmişse ya da işin
     * son deneme hakkıysa geçilir (sahne sessiz kalmasın). Masalın sağlayıcısı yoksa hepsi, uygunluk sırasıyla.
     */
    public List<String> order(String preferred, boolean lastAttempt) {
        List<Health> ranked = rank();
        List<String> names = new ArrayList<>(ranked.stream().map(Health::name).toList());
        if (preferred == null || !names.remove(preferred)) {
            return names;
        }
        Health own = ranked.stream().filter(h -> h.name().equals(preferred)).findFirst().orElseThrow();
        if (!lastAttempt && !own.outOfQuota(Instant.now())) {
            return List.of(preferred);
        }
        names.addFirst(preferred);
        return names;
    }

    /** Etkin, adaptörü yüklü TTS sağlayıcıları; en uygun olan başta. */
    public List<Health> rank() {
        Set<String> available = clients.stream().filter(c -> c instanceof TtsClient).map(AiClient::providerName).collect(Collectors.toSet());
        List<AiProviderConfig> active = configs.findByProviderTypeAndActiveTrueOrderByPriorityAsc(AiProviderConfig.Type.TTS).stream()
                .filter(c -> available.contains(c.getProviderName())).toList();
        if (active.isEmpty()) {
            return List.of();
        }
        Map<String, long[]> usage = new HashMap<>();
        jdbc.query("""
                SELECT provider_name,
                       count(*) FILTER (WHERE is_success AND created_at >= %s) AS today,
                       count(*) FILTER (WHERE is_success AND created_at >= %s) AS month
                FROM ai_usage_events WHERE provider_type = 'TTS' AND created_at >= %s GROUP BY provider_name""".formatted(TODAY, MONTH, MONTH),
                rs -> {
                    usage.put(rs.getString("provider_name"), new long[]{rs.getLong("today"), rs.getLong("month")});
                });
        Map<String, Instant> blocked = new HashMap<>();
        jdbc.query("""
                SELECT provider_name, http_status, max(created_at) AS at FROM ai_usage_events
                WHERE provider_type = 'TTS' AND NOT is_success AND http_status IN (402, 429, 503) AND created_at >= now() - interval '24 hours'
                GROUP BY provider_name, http_status""",
                rs -> {
                    Instant at = rs.getTimestamp("at").toInstant();
                    Instant until = at.plus(rs.getInt("http_status") == 402 ? QUOTA_COOLDOWN : BUSY_COOLDOWN);
                    blocked.merge(rs.getString("provider_name"), until, (a, b) -> a.isAfter(b) ? a : b);
                });
        // Kota hatasından sonra başarılı bir çağrı olduysa engel kalkar
        jdbc.query("""
                SELECT provider_name, max(created_at) AS at FROM ai_usage_events
                WHERE provider_type = 'TTS' AND is_success AND created_at >= now() - interval '24 hours' GROUP BY provider_name""",
                rs -> {
                    String name = rs.getString("provider_name");
                    Timestamp ok = rs.getTimestamp("at");
                    blocked.computeIfPresent(name, (k, until) -> lastFailure(k).isBefore(ok.toInstant()) ? null : until);
                });
        Map<String, Long> lastStory = new HashMap<>();
        jdbc.query("SELECT tts_provider, max(id) AS last FROM stories WHERE tts_provider IS NOT NULL GROUP BY tts_provider",
                rs -> {
                    lastStory.put(rs.getString("tts_provider"), rs.getLong("last"));
                });
        Map<String, UsageMeter.RateLimit> reported = UsageMeter.rateLimits();

        Instant now = Instant.now();
        List<Health> list = new ArrayList<>();
        for (AiProviderConfig c : active) {
            long[] u = usage.getOrDefault(c.getProviderName(), new long[2]);
            list.add(new Health(c.getProviderName(), c.getPriority(), u[0], u[1], c.getDailyLimit(), c.getMonthlyLimit(),
                    remainingCharacters(reported.get(c.getProviderName())), blocked.get(c.getProviderName()),
                    lastStory.getOrDefault(c.getProviderName(), -1L)));
        }
        list.sort(Comparator.comparing((Health h) -> h.blocked(now))
                .thenComparing(Health::nearLimit)
                .thenComparingLong(Health::lastStoryId)
                .thenComparingInt(Health::priority));
        return list;
    }

    private Instant lastFailure(String provider) {
        Timestamp t = jdbc.queryForObject("""
                SELECT max(created_at) FROM ai_usage_events WHERE provider_type = 'TTS' AND provider_name = ? AND NOT is_success
                AND http_status IN (402, 429, 503)""", Timestamp.class, provider);
        return t == null ? Instant.EPOCH : t.toInstant();
    }

    private static Long remainingCharacters(UsageMeter.RateLimit limit) {
        if (limit == null) {
            return null;
        }
        String v = limit.headers().get("kalan-karakter");
        try {
            return v == null ? null : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String describe(Health h) {
        Instant now = Instant.now();
        String state = h.blocked(now) ? "kullanılamaz" : h.nearLimit() ? "kotaya yakın" : "uygun";
        return h.name() + "[" + state + ", bugün " + h.todayCalls() + (h.dailyLimit() == null ? "" : "/" + h.dailyLimit())
                + (h.remainingCharacters() == null ? "" : ", kalan " + h.remainingCharacters() + " karakter") + "]";
    }
}
