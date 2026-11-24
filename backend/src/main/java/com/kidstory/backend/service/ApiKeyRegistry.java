package com.kidstory.backend.service;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.service.ai.ApiCredential;
import com.kidstory.backend.service.ai.ApiKeyRing;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servis başına .env anahtarları. Birincil anahtar mevcut ayardan (GEMINI_API_KEY → app.ai.gemini.api-key), yedekler
 * aynı adın sonuna numara eklenerek okunur: GEMINI_API_KEY_2 ... GEMINI_API_KEY_9 (her biri ayrı hesabın ücretsiz kotası).
 * Cloudflare'de yedek hesap kimliği CLOUDFLARE_ACCOUNT_ID_2, Azure'da bölge AZURE_SPEECH_REGION_2 (yoksa birincilinki).
 * Anahtarlar veritabanına yazılmaz; durumları ApiKeyStateService'te tutulur.
 */
@Component
public class ApiKeyRegistry {

    static final int MAX_SLOTS = 9;

    /** Panelde gösterilecek servis tanımı. */
    public record Service(String name, AiProviderConfig.Type type, String label, String envName) {
    }

    private static final List<Service> SERVICES = List.of(
            new Service("groq", AiProviderConfig.Type.LLM, "Groq", "GROQ_API_KEY"),
            new Service("featherless", AiProviderConfig.Type.LLM, "Featherless", "FEATHERLESS_API_KEY"),
            new Service("gemini", AiProviderConfig.Type.SCENE, "Gemini", "GEMINI_API_KEY"),
            new Service("gemini-lite", AiProviderConfig.Type.SCENE, "Gemini (hafif model)", "GEMINI_API_KEY"),
            new Service("cloudflare", AiProviderConfig.Type.IMAGE, "Cloudflare Workers AI", "CLOUDFLARE_API_TOKEN"),
            new Service("gemini-tts", AiProviderConfig.Type.TTS, "Gemini TTS", "GEMINI_API_KEY"),
            new Service("murf", AiProviderConfig.Type.TTS, "Murf AI", "MURF_API_KEY"),
            new Service("azure", AiProviderConfig.Type.TTS, "Azure Speech", "AZURE_SPEECH_KEY"));

    private static final Map<String, String> PRIMARY_PROPERTY = Map.of(
            "GROQ_API_KEY", "app.ai.groq.api-key",
            "FEATHERLESS_API_KEY", "app.ai.featherless.api-key",
            "GEMINI_API_KEY", "app.ai.gemini.api-key",
            "CLOUDFLARE_API_TOKEN", "app.ai.cloudflare.api-token",
            "MURF_API_KEY", "app.ai.murf.api-key",
            "AZURE_SPEECH_KEY", "app.ai.azure-speech.key");

    private final Environment env;
    private final ApiKeyStateService states;
    private final Map<String, ApiKeyRing> rings = new LinkedHashMap<>();

    public ApiKeyRegistry(Environment env, ApiKeyStateService states) {
        this.env = env;
        this.states = states;
        for (Service s : SERVICES) {
            rings.put(s.name(), new ApiKeyRing(s.name(), credentials(s), states));
        }
    }

    public List<Service> services() {
        return SERVICES;
    }

    public ApiKeyRing ring(String service) {
        ApiKeyRing ring = rings.get(service);
        if (ring == null) {
            throw new IllegalArgumentException("Bilinmeyen servis: " + service);
        }
        return ring;
    }

    public boolean known(String service, int slot) {
        ApiKeyRing ring = rings.get(service);
        return ring != null && ring.credentials().stream().anyMatch(c -> c.slot() == slot);
    }

    private List<ApiCredential> credentials(Service s) {
        List<ApiCredential> list = new ArrayList<>();
        for (int slot = 1; slot <= MAX_SLOTS; slot++) {
            String envName = slot == 1 ? s.envName() : s.envName() + "_" + slot;
            String secret = slot == 1 ? env.getProperty(PRIMARY_PROPERTY.get(s.envName()), "") : env.getProperty(envName, "");
            if (secret == null || secret.isBlank()) {
                continue;
            }
            list.add(new ApiCredential(slot, envName, secret.strip(), account(s, slot), baseUrl(s, slot)));
        }
        return list;
    }

    private String account(Service s, int slot) {
        if (!"cloudflare".equals(s.name())) {
            return null;
        }
        String primary = env.getProperty("app.ai.cloudflare.account-id", "");
        return slot == 1 ? primary : env.getProperty("CLOUDFLARE_ACCOUNT_ID_" + slot, primary);
    }

    private String baseUrl(Service s, int slot) {
        if (!"azure".equals(s.name())) {
            return null;
        }
        String primary = env.getProperty("app.ai.azure-speech.base-url", "");
        String region = slot == 1 ? null : env.getProperty("AZURE_SPEECH_REGION_" + slot);
        return region == null || region.isBlank() ? primary : "https://" + region.strip() + ".tts.speech.microsoft.com";
    }
}
