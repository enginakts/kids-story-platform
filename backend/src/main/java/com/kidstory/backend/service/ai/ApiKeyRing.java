package com.kidstory.backend.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Bir servisin (ör. gemini-tts) .env'deki anahtarları, birincilden yedeğe sırayla. Her çağrı ilk açık anahtarla yapılır;
 * anahtar 401/402/403 ya da 429 alırsa otomatik kapatılır ve aynı çağrı sıradaki anahtarla denenir. Açık anahtar
 * kalmazsa hata yönlendiriciye (AiRouter) döner ve o da sıradaki sağlayıcıya geçer.
 */
public final class ApiKeyRing {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRing.class);

    private final String service;
    private final List<ApiCredential> credentials;
    private final ApiKeyStates states;

    public ApiKeyRing(String service, List<ApiCredential> credentials, ApiKeyStates states) {
        this.service = service;
        this.credentials = List.copyOf(credentials);
        this.states = states;
    }

    /** Tek anahtar, durum kaydı yok (birim testleri ve doğrudan kurulan adaptörler). */
    public static ApiKeyRing single(String service, String secret) {
        return new ApiKeyRing(service, List.of(ApiCredential.of(secret)), ApiKeyStates.ALWAYS);
    }

    public String service() {
        return service;
    }

    public List<ApiCredential> credentials() {
        return credentials;
    }

    public boolean hasUsable() {
        return credentials.stream().anyMatch(c -> states.usable(service, c.slot()));
    }

    public <R> R call(Function<ApiCredential, R> attempt) {
        RuntimeException last = null;
        for (ApiCredential credential : credentials) {
            if (!states.usable(service, credential.slot())) {
                continue;
            }
            states.used(service, credential.slot());
            try {
                return attempt.apply(credential);
            } catch (RateLimitedException e) {
                if (e.getStatus() != 429) {
                    throw e; // 503: sağlayıcı yoğun, anahtarla ilgisi yok
                }
                disable(credential, 429, e.getMessage());
                last = e;
            } catch (KeyRejectedException e) {
                disable(credential, e.getStatus(), e.getMessage());
                last = e;
            }
        }
        if (last == null) {
            throw new IllegalStateException(service + " için açık API anahtarı yok");
        }
        throw last;
    }

    private void disable(ApiCredential credential, int status, String reason) {
        log.warn("{} anahtarı {} otomatik kapatıldı (HTTP {}); varsa yedek anahtara geçiliyor", service, credential, status);
        states.autoDisable(service, credential.slot(), status, reason);
    }
}
