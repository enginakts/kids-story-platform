package com.kidstory.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare Workers AI'nin resmî günlük kullanımı (nöron), GraphQL analiz API'sinden. Görsel üretmez, kota harcamaz.
 * Rapor sayfası açılınca en fazla 5 dakikada bir çağrılır. API anahtarında "Account Analytics: Read" izni yoksa
 * sayfada nedeni yazılır. Cloudflare günü UTC 00:00'da başlar.
 */
@Slf4j
@Service
public class CloudflareUsageService {

    private static final Duration CACHE = Duration.ofMinutes(5);
    private static final String QUERY = """
            query($account: String!, $day: Date!) { viewer { accounts(filter: {accountTag: $account}) {
              aiInferenceAdaptiveGroups(filter: {date_geq: $day, date_leq: $day}, limit: 100) {
                count sum { totalNeurons } dimensions { modelId } } } } }""";

    /** available=false ise message nedeni söyler. dailyNeurons: ücretsiz günlük pay (ayarlanabilir). */
    public record Usage(boolean configured, boolean available, long neurons, long requests, long dailyNeurons,
                        String message, Instant fetchedAt) {

        public long remaining() {
            return Math.max(0, dailyNeurons - neurons);
        }

        /** Türkiye saatiyle son güncelleme (şablon statik sınıf kullanamaz). */
        public java.time.LocalDateTime fetchedAtLocal() {
            return java.time.LocalDateTime.ofInstant(fetchedAt, java.time.ZoneId.of("Europe/Istanbul"));
        }

        public int percent() {
            return dailyNeurons <= 0 ? 0 : (int) Math.min(100, Math.round(neurons * 100.0 / dailyNeurons));
        }
    }

    private final RestClient client;
    private final String accountId;
    private final String apiToken;
    private final long dailyNeurons;
    private volatile Usage cached;

    public CloudflareUsageService(RestClient.Builder builder,
                                  @Value("${app.ai.cloudflare.base-url:https://api.cloudflare.com/client/v4}") String baseUrl,
                                  @Value("${app.ai.cloudflare.account-id:}") String accountId,
                                  @Value("${app.ai.cloudflare.api-token:}") String apiToken,
                                  @Value("${app.ai.cloudflare.daily-neurons:10000}") long dailyNeurons) {
        this.client = builder.clone().baseUrl(baseUrl.replaceAll("/+$", "")).build();
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.dailyNeurons = dailyNeurons;
    }

    public Usage today() {
        Usage c = cached;
        if (c != null && c.fetchedAt().plus(CACHE).isAfter(Instant.now())) {
            return c;
        }
        Usage fresh = fetch();
        cached = fresh;
        return fresh;
    }

    @SuppressWarnings("unchecked")
    private Usage fetch() {
        if (accountId.isBlank() || apiToken.isBlank()) {
            return new Usage(false, false, 0, 0, dailyNeurons, "Cloudflare anahtarı tanımlı değil.", Instant.now());
        }
        try {
            Map<String, Object> body = client.post().uri("/graphql")
                    .header("Authorization", "Bearer " + apiToken)
                    .body(Map.of("query", QUERY, "variables", Map.of("account", accountId, "day", LocalDate.now(ZoneOffset.UTC).toString())))
                    .retrieve().body(Map.class);
            List<Map<String, Object>> errors = body == null ? null : (List<Map<String, Object>>) body.get("errors");
            if (errors != null && !errors.isEmpty()) {
                String msg = String.valueOf(errors.getFirst().get("message"));
                return new Usage(true, false, 0, 0, dailyNeurons, msg.contains("not authorized")
                        ? "API anahtarında \"Account Analytics: Read\" izni yok. Cloudflare panelinden bu izni ekleyin."
                        : "Cloudflare analiz hatası: " + msg, Instant.now());
            }
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            Map<String, Object> viewer = (Map<String, Object>) data.get("viewer");
            List<Map<String, Object>> accounts = (List<Map<String, Object>>) viewer.get("accounts");
            long neurons = 0, requests = 0;
            if (accounts != null && !accounts.isEmpty()) {
                List<Map<String, Object>> groups = (List<Map<String, Object>>) accounts.getFirst().get("aiInferenceAdaptiveGroups");
                for (Map<String, Object> g : groups == null ? List.<Map<String, Object>>of() : groups) {
                    requests += ((Number) g.getOrDefault("count", 0)).longValue();
                    Map<String, Object> sum = (Map<String, Object>) g.get("sum");
                    Object n = sum == null ? null : sum.get("totalNeurons");
                    neurons += n == null ? 0 : Math.round(((Number) n).doubleValue());
                }
            }
            return new Usage(true, true, neurons, requests, dailyNeurons, null, Instant.now());
        } catch (RuntimeException e) {
            log.warn("Cloudflare kullanım bilgisi alınamadı: {}", e.toString());
            return new Usage(true, false, 0, 0, dailyNeurons, "Cloudflare analiz API'sine ulaşılamadı.", Instant.now());
        }
    }
}
