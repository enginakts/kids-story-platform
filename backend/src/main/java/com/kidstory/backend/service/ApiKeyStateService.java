package com.kidstory.backend.service;

import com.kidstory.backend.service.ai.ApiKeyStates;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Anahtar durumları (V22 api_key_states). Otomatik kapanan anahtar, sağlayıcının ücretsiz kotasının yenilendiği
 * saatte kendiliğinden yeniden açılır (Gemini: Pasifik saatiyle gece yarısı; diğerleri: UTC gece yarısı).
 * 401/403 (geçersiz anahtar) ve yöneticinin kapattığı anahtarlar kendiliğinden açılmaz.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyStateService implements ApiKeyStates {

    private static final ZoneId TR = ZoneId.of("Europe/Istanbul");

    private final JdbcTemplate jdbc;

    /** Panel için bir anahtarın durumu. */
    public record State(boolean enabled, String disabledBy, String reason, Instant disabledAt, Instant reenableAt,
                        String lastError, Instant lastErrorAt, Instant lastUsedAt, int callsToday) {
        static final State FRESH = new State(true, null, null, null, null, null, null, null, 0);
    }

    @Override
    public boolean usable(String service, int slot) {
        // true: açık; false: kapalı; null: otomatik kapanmıştı ve yeniden açılma vakti geldi
        Boolean state = jdbc.query("SELECT enabled, disabled_by, reenable_at FROM api_key_states WHERE service = ? AND slot = ?", rs -> {
            if (!rs.next() || rs.getBoolean("enabled")) {
                return Boolean.TRUE;
            }
            Timestamp reenable = rs.getTimestamp("reenable_at");
            boolean due = "AUTO".equals(rs.getString("disabled_by")) && reenable != null && !reenable.toInstant().isAfter(Instant.now());
            return due ? null : Boolean.FALSE;
        }, service, slot);
        if (state != null) {
            return state;
        }
        // Kota yenilendi: otomatik kapanan anahtar yeniden açılır
        jdbc.update("""
                UPDATE api_key_states SET enabled = TRUE, disabled_by = NULL, disabled_reason = NULL, disabled_at = NULL,
                    reenable_at = NULL, updated_at = now() WHERE service = ? AND slot = ? AND disabled_by = 'AUTO'""", service, slot);
        return true;
    }

    @Override
    public void used(String service, int slot) {
        LocalDate today = LocalDate.now(TR);
        jdbc.update("""
                INSERT INTO api_key_states (service, slot, last_used_at, calls_day, calls_today) VALUES (?, ?, now(), ?, 1)
                ON CONFLICT (service, slot) DO UPDATE SET last_used_at = now(),
                    calls_today = CASE WHEN api_key_states.calls_day = EXCLUDED.calls_day THEN api_key_states.calls_today + 1 ELSE 1 END,
                    calls_day = EXCLUDED.calls_day""", service, slot, today);
    }

    @Override
    public void autoDisable(String service, int slot, int status, String reason) {
        String text = cut(reasonText(status) + (reason == null ? "" : " · " + reason));
        Instant reenable = status == 401 || status == 403 ? null : nextReset(service, Instant.now());
        jdbc.update("""
                INSERT INTO api_key_states (service, slot, enabled, disabled_by, disabled_reason, disabled_at, reenable_at, last_error, last_error_at)
                VALUES (?, ?, FALSE, 'AUTO', ?, now(), ?, ?, now())
                ON CONFLICT (service, slot) DO UPDATE SET enabled = FALSE,
                    disabled_by = CASE WHEN api_key_states.disabled_by = 'ADMIN' THEN 'ADMIN' ELSE 'AUTO' END,
                    disabled_reason = EXCLUDED.disabled_reason, disabled_at = now(), reenable_at = EXCLUDED.reenable_at,
                    last_error = EXCLUDED.last_error, last_error_at = now(), updated_at = now()""",
                service, slot, text, reenable == null ? null : Timestamp.from(reenable), text);
    }

    /** Yönetici açar/kapatır. Açınca otomatik kapanma bilgisi silinir; kapatınca kendiliğinden açılmaz. */
    public void setByAdmin(String service, int slot, boolean enabled, long adminId) {
        jdbc.update("""
                INSERT INTO api_key_states (service, slot, enabled, disabled_by, disabled_reason, disabled_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (service, slot) DO UPDATE SET enabled = EXCLUDED.enabled, disabled_by = EXCLUDED.disabled_by,
                    disabled_reason = EXCLUDED.disabled_reason, disabled_at = EXCLUDED.disabled_at, reenable_at = NULL,
                    updated_by = EXCLUDED.updated_by, updated_at = now()""",
                service, slot, enabled, enabled ? null : "ADMIN", enabled ? null : "Yönetici kapattı",
                enabled ? null : Timestamp.from(Instant.now()), adminId);
    }

    /** Kayıtlı durumlar: "servis#slot" → durum. Kaydı olmayan anahtar açıktır. */
    public Map<String, State> all() {
        Map<String, State> map = new HashMap<>();
        LocalDate today = LocalDate.now(TR);
        jdbc.query("SELECT * FROM api_key_states", rs -> {
            LocalDate day = rs.getObject("calls_day", LocalDate.class);
            map.put(rs.getString("service") + "#" + rs.getInt("slot"), new State(rs.getBoolean("enabled"), rs.getString("disabled_by"),
                    rs.getString("disabled_reason"), instant(rs.getTimestamp("disabled_at")), instant(rs.getTimestamp("reenable_at")),
                    rs.getString("last_error"), instant(rs.getTimestamp("last_error_at")), instant(rs.getTimestamp("last_used_at")),
                    today.equals(day) ? rs.getInt("calls_today") : 0));
        });
        return map;
    }

    public State state(Map<String, State> all, String service, int slot) {
        return all.getOrDefault(service + "#" + slot, State.FRESH);
    }

    /** Ücretsiz kotanın yenilendiği an: Gemini Pasifik saatiyle, diğerleri UTC gece yarısı. */
    static Instant nextReset(String service, Instant now) {
        ZoneId zone = service.startsWith("gemini") ? ZoneId.of("America/Los_Angeles") : ZoneId.of("UTC");
        return ZonedDateTime.ofInstant(now, zone).truncatedTo(ChronoUnit.DAYS).plusDays(1).toInstant();
    }

    static String reasonText(int status) {
        return switch (status) {
            case 429 -> "Kota/hız sınırı doldu (HTTP 429)";
            case 402 -> "Kota/kredi bitti (HTTP 402)";
            case 401 -> "Anahtar geçersiz (HTTP 401)";
            case 403 -> "Anahtar yetkisiz (HTTP 403)";
            default -> "HTTP " + status;
        };
    }

    private static String cut(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
