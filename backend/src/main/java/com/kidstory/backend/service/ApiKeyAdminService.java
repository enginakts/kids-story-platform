package com.kidstory.backend.service;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.repository.AiProviderConfigRepository;
import com.kidstory.backend.service.ai.AiClient;
import com.kidstory.backend.service.ai.ApiCredential;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Süper yönetici "API anahtarları" sayfası (V22): servis başına .env'deki birincil ve yedek anahtarlar, durumları,
 * bugünkü kullanım; anahtarı kapatma/açma (admin_audit_logs'a yazılır). Anahtarın kendisi gösterilmez, son 4 hane görünür.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyAdminService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("tr"))
            .withZone(ZoneId.of("Europe/Istanbul"));
    private static final Map<AiProviderConfig.Type, String> TITLES = Map.of(
            AiProviderConfig.Type.LLM, "Masal metni", AiProviderConfig.Type.SCENE, "Sahnelere bölme",
            AiProviderConfig.Type.IMAGE, "Görsel", AiProviderConfig.Type.TTS, "Seslendirme");

    private final ApiKeyRegistry registry;
    private final ApiKeyStateService states;
    private final AiProviderConfigRepository providers;
    private final List<AiClient> clients;
    private final AdminService admin;

    public record KeyRow(String service, int slot, String envName, String masked, boolean primary, boolean enabled,
                         boolean auto, String reason, String reenableAt, String lastError, String lastErrorAt,
                         String lastUsedAt, int callsToday) {
    }

    public record ServiceRow(String name, String label, String envName, String model, boolean providerActive,
                             boolean loaded, int priority, List<KeyRow> keys, long usable) {
    }

    public record Group(String title, List<ServiceRow> services) {
    }

    public List<Group> overview() {
        Map<String, ApiKeyStateService.State> all = states.all();
        Set<String> loaded = clients.stream().map(AiClient::providerName).collect(Collectors.toSet());
        Map<String, AiProviderConfig> configs = providers.findAll().stream()
                .collect(Collectors.toMap(AiProviderConfig::getProviderName, c -> c, (a, b) -> a));
        List<Group> groups = new ArrayList<>();
        for (AiProviderConfig.Type type : List.of(AiProviderConfig.Type.LLM, AiProviderConfig.Type.SCENE,
                AiProviderConfig.Type.IMAGE, AiProviderConfig.Type.TTS)) {
            List<ServiceRow> rows = new ArrayList<>();
            for (ApiKeyRegistry.Service s : registry.services()) {
                if (s.type() != type) {
                    continue;
                }
                AiProviderConfig config = configs.get(s.name());
                List<KeyRow> keys = new ArrayList<>();
                for (ApiCredential c : registry.ring(s.name()).credentials()) {
                    ApiKeyStateService.State st = states.state(all, s.name(), c.slot());
                    boolean enabled = st.enabled() || "AUTO".equals(st.disabledBy()) && st.reenableAt() != null
                            && !st.reenableAt().isAfter(Instant.now());
                    keys.add(new KeyRow(s.name(), c.slot(), c.envName(), c.masked(), c.slot() == 1, enabled,
                            "AUTO".equals(st.disabledBy()), enabled ? null : st.reason(), enabled ? null : time(st.reenableAt()),
                            st.lastError(), time(st.lastErrorAt()), time(st.lastUsedAt()), st.callsToday()));
                }
                rows.add(new ServiceRow(s.name(), s.label(), s.envName(), config == null ? null : config.getActiveModelName(),
                        config != null && config.isActive(), loaded.contains(s.name()), config == null ? 0 : config.getPriority(),
                        keys, keys.stream().filter(KeyRow::enabled).count()));
            }
            rows.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
            groups.add(new Group(TITLES.get(type), rows));
        }
        return groups;
    }

    @Transactional
    public void setEnabled(long adminId, String service, int slot, boolean enabled, String ip) {
        if (!registry.known(service, slot)) {
            throw new IllegalArgumentException("Böyle bir anahtar yok");
        }
        states.setByAdmin(service, slot, enabled, adminId);
        String env = registry.ring(service).credentials().stream().filter(c -> c.slot() == slot)
                .map(ApiCredential::envName).findFirst().orElse("#" + slot);
        admin.audit(adminId, enabled ? "API_KEY_ENABLED" : "API_KEY_DISABLED", "api_key", null, service + " · " + env, ip);
    }

    private static String time(Instant t) {
        return t == null ? null : TIME.format(t);
    }
}
