package com.kidstory.backend.service;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.service.ai.UsageMeter;
import com.kidstory.backend.service.ai.UsageRecorder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Yapay zekâ kullanım ve maliyet raporu (V14). Yönlendirici (AiRouter) her dış çağrı denemesini buraya yazar;
 * rapor yalnızca bu kayıtlardan ve yönetimden girilen limit/ücretlerden hesaplanır (ek dış çağrı yok).
 * Gün ve ay sınırları Türkiye saatine göredir. Kayıt hatası masal üretimini asla durdurmaz.
 */
@Service
@RequiredArgsConstructor
public class AiUsageService implements UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);
    private static final String TZ = "Europe/Istanbul";
    private static final String TODAY = "(date_trunc('day', now() AT TIME ZONE '" + TZ + "') AT TIME ZONE '" + TZ + "')";
    private static final String MONTH = "(date_trunc('month', now() AT TIME ZONE '" + TZ + "') AT TIME ZONE '" + TZ + "')";

    private final JdbcTemplate jdbc;

    @Override
    public void record(AiProviderConfig config, boolean success, String error, long durationMs,
                       UsageMeter.Reading reading, UsageMeter.Context context) {
        try {
            jdbc.update("""
                    INSERT INTO ai_usage_events (provider_type, provider_name, model_name, story_id, job_id, is_success,
                        http_status, error_message, duration_ms, input_tokens, output_tokens, estimated_cost)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    config.getProviderType().name(), config.getProviderName(), config.getActiveModelName(),
                    context.storyId(), context.jobId(), success, reading.httpStatus(), truncate(error),
                    (int) Math.min(Integer.MAX_VALUE, durationMs), reading.inputTokens(), reading.outputTokens(),
                    cost(config, reading));
        } catch (RuntimeException e) {
            log.warn("Kullanım kaydı yazılamadı ({}): {}", config.getProviderName(), e.toString());
        }
    }

    /** Tahmini maliyet: çağrı başı ücret + (giriş + çıkış token) / 1000 × 1K token ücreti. */
    static BigDecimal cost(AiProviderConfig config, UsageMeter.Reading reading) {
        BigDecimal perCall = config.getCostPerCall() == null ? BigDecimal.ZERO : config.getCostPerCall();
        BigDecimal per1k = config.getCostPer1kTokens() == null ? BigDecimal.ZERO : config.getCostPer1kTokens();
        int tokens = (reading.inputTokens() == null ? 0 : reading.inputTokens()) + (reading.outputTokens() == null ? 0 : reading.outputTokens());
        return perCall.add(per1k.multiply(BigDecimal.valueOf(tokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 300 ? error : error.substring(0, 297) + "...";
    }

    // ---------- Rapor ----------

    public record ProviderUsage(long id, String type, String name, String model, boolean active, int priority,
                                Integer dailyLimit, Integer monthlyLimit, BigDecimal costPerCall, BigDecimal costPer1kTokens,
                                long todayCalls, long todayFailed, long monthCalls, long monthFailed, long monthTokens,
                                BigDecimal todayCost, BigDecimal monthCost, Long lastHttpStatus, LocalDateTime lastCallAt) {

        /** Limit girildiyse kalan çağrı (eksiye düşmez). */
        public Long dailyRemaining() {
            return dailyLimit == null ? null : Math.max(0, dailyLimit - todayCalls);
        }

        public Long monthlyRemaining() {
            return monthlyLimit == null ? null : Math.max(0, monthlyLimit - monthCalls);
        }

        /** Günlük limitin yüzde kaçı kullanıldı (0-100); limit yoksa null. */
        public Integer dailyPercent() {
            return dailyLimit == null ? null : (int) Math.min(100, Math.round(todayCalls * 100.0 / dailyLimit));
        }

        public Integer monthlyPercent() {
            return monthlyLimit == null ? null : (int) Math.min(100, Math.round(monthCalls * 100.0 / monthlyLimit));
        }
    }

    public record Totals(long calls, long failed, long tokens, BigDecimal cost, long images) {
    }

    public record DayRow(LocalDate day, long text, long scene, long image, long tts, long failed, BigDecimal cost) {
    }

    public record FailureRow(LocalDateTime at, String type, String provider, String model, Integer httpStatus, String error, Long storyId) {
    }

    public record Report(List<ProviderUsage> providers, Totals today, Totals month, List<DayRow> days, List<FailureRow> failures,
                         Map<String, UsageMeter.RateLimit> rateLimits) {
    }

    public Report report() {
        List<ProviderUsage> providers = jdbc.query("""
                SELECT c.id, c.provider_type, c.provider_name, c.active_model_name, c.is_active, c.priority,
                       c.daily_limit, c.monthly_limit, c.cost_per_call, c.cost_per_1k_tokens,
                       count(e.id) FILTER (WHERE e.created_at >= %1$s) AS today_calls,
                       count(e.id) FILTER (WHERE e.created_at >= %1$s AND NOT e.is_success) AS today_failed,
                       count(e.id) FILTER (WHERE e.created_at >= %2$s) AS month_calls,
                       count(e.id) FILTER (WHERE e.created_at >= %2$s AND NOT e.is_success) AS month_failed,
                       coalesce(sum(coalesce(e.input_tokens, 0) + coalesce(e.output_tokens, 0)) FILTER (WHERE e.created_at >= %2$s), 0) AS month_tokens,
                       coalesce(sum(e.estimated_cost) FILTER (WHERE e.created_at >= %1$s), 0) AS today_cost,
                       coalesce(sum(e.estimated_cost) FILTER (WHERE e.created_at >= %2$s), 0) AS month_cost,
                       (SELECT l.http_status FROM ai_usage_events l WHERE l.provider_type = c.provider_type
                            AND l.provider_name = c.provider_name ORDER BY l.created_at DESC LIMIT 1) AS last_status,
                       max(e.created_at) AS last_at
                FROM ai_providers_config c
                LEFT JOIN ai_usage_events e ON e.provider_type = c.provider_type AND e.provider_name = c.provider_name
                     AND e.created_at >= least(%1$s, %2$s)
                GROUP BY c.id
                ORDER BY CASE c.provider_type WHEN 'LLM' THEN 0 WHEN 'SCENE' THEN 1 WHEN 'IMAGE' THEN 2 ELSE 3 END, c.priority, c.provider_name
                """.formatted(TODAY, MONTH),
                (r, i) -> new ProviderUsage(r.getLong("id"), r.getString("provider_type"), r.getString("provider_name"),
                        r.getString("active_model_name"), r.getBoolean("is_active"), r.getInt("priority"),
                        (Integer) r.getObject("daily_limit"), (Integer) r.getObject("monthly_limit"),
                        r.getBigDecimal("cost_per_call"), r.getBigDecimal("cost_per_1k_tokens"),
                        r.getLong("today_calls"), r.getLong("today_failed"), r.getLong("month_calls"), r.getLong("month_failed"),
                        r.getLong("month_tokens"), r.getBigDecimal("today_cost"), r.getBigDecimal("month_cost"),
                        r.getObject("last_status") == null ? null : r.getLong("last_status"),
                        localTime(r.getTimestamp("last_at"))));
        return new Report(providers, totals(TODAY), totals(MONTH), days(), failures(), UsageMeter.rateLimits());
    }

    private Totals totals(String since) {
        return jdbc.queryForObject("""
                SELECT count(*) AS calls, count(*) FILTER (WHERE NOT is_success) AS failed,
                       coalesce(sum(coalesce(input_tokens, 0) + coalesce(output_tokens, 0)), 0) AS tokens,
                       coalesce(sum(estimated_cost), 0) AS cost,
                       count(*) FILTER (WHERE provider_type = 'IMAGE' AND is_success) AS images
                FROM ai_usage_events WHERE created_at >= %s""".formatted(since),
                (r, i) -> new Totals(r.getLong("calls"), r.getLong("failed"), r.getLong("tokens"), r.getBigDecimal("cost"), r.getLong("images")));
    }

    /** Son 14 gün, görev türüne göre çağrı sayıları (Türkiye saatiyle). */
    private List<DayRow> days() {
        return jdbc.query("""
                SELECT d::date AS day,
                       count(e.id) FILTER (WHERE e.provider_type = 'LLM') AS text_calls,
                       count(e.id) FILTER (WHERE e.provider_type = 'SCENE') AS scene_calls,
                       count(e.id) FILTER (WHERE e.provider_type = 'IMAGE') AS image_calls,
                       count(e.id) FILTER (WHERE e.provider_type = 'TTS') AS tts_calls,
                       count(e.id) FILTER (WHERE NOT e.is_success) AS failed,
                       coalesce(sum(e.estimated_cost), 0) AS cost
                FROM generate_series((now() AT TIME ZONE '%1$s')::date - 13, (now() AT TIME ZONE '%1$s')::date, interval '1 day') AS d
                LEFT JOIN ai_usage_events e ON (e.created_at AT TIME ZONE '%1$s')::date = d::date
                GROUP BY d ORDER BY d DESC""".formatted(TZ),
                (r, i) -> new DayRow(r.getDate("day").toLocalDate(), r.getLong("text_calls"), r.getLong("scene_calls"),
                        r.getLong("image_calls"), r.getLong("tts_calls"), r.getLong("failed"), r.getBigDecimal("cost")));
    }

    private List<FailureRow> failures() {
        return jdbc.query("""
                SELECT created_at, provider_type, provider_name, model_name, http_status, error_message, story_id
                FROM ai_usage_events WHERE NOT is_success ORDER BY created_at DESC LIMIT 10""",
                (r, i) -> new FailureRow(localTime(r.getTimestamp("created_at")), r.getString("provider_type"), r.getString("provider_name"),
                        r.getString("model_name"), (Integer) r.getObject("http_status"), r.getString("error_message"),
                        r.getObject("story_id") == null ? null : r.getLong("story_id")));
    }

    private static LocalDateTime localTime(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atZone(java.time.ZoneId.of(TZ)).toLocalDateTime();
    }
}
