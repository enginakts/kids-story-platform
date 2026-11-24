package com.kidstory.backend.service.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Anahtar halkası (V22): kota/yetki hatasında anahtar kapanır ve aynı çağrı yedek anahtarla yapılır. */
class ApiKeyRingTests {

    /** Bellekte durum: kapatılan anahtarlar ve nedenleri. */
    static class Memory implements ApiKeyStates {
        final Map<Integer, Integer> disabled = new HashMap<>();
        final List<Integer> used = new ArrayList<>();

        @Override
        public boolean usable(String service, int slot) {
            return !disabled.containsKey(slot);
        }

        @Override
        public void used(String service, int slot) {
            used.add(slot);
        }

        @Override
        public void autoDisable(String service, int slot, int status, String reason) {
            disabled.put(slot, status);
        }
    }

    private final Memory states = new Memory();
    private final ApiKeyRing ring = new ApiKeyRing("gemini-tts", List.of(
            new ApiCredential(1, "GEMINI_API_KEY", "birincil-anahtar-1111", null, null),
            new ApiCredential(2, "GEMINI_API_KEY_2", "yedek-anahtar-2222", null, null),
            new ApiCredential(3, "GEMINI_API_KEY_3", "yedek-anahtar-3333", null, null)), states);

    @Test
    void quotaErrorDisablesKeyAndSameCallUsesBackup() {
        String result = ring.call(key -> {
            if (key.slot() == 1) {
                throw new RateLimitedException("Gemini TTS", 30);
            }
            return "ses:" + key.secret();
        });
        assertThat(result).isEqualTo("ses:yedek-anahtar-2222");
        assertThat(states.disabled).containsEntry(1, 429);
        // Sonraki çağrı doğrudan yedekle başlar
        assertThat(ring.call(ApiCredential::envName)).isEqualTo("GEMINI_API_KEY_2");
        assertThat(states.used).containsExactly(1, 2, 2);
    }

    @Test
    void rejectedKeyIsDisabledButBusyProviderIsNot() {
        assertThat(ring.<Integer>call(key -> {
            if (key.slot() == 1) {
                throw new KeyRejectedException("Murf karakter kotası bitti (HTTP 402)", 402);
            }
            return key.slot();
        })).isEqualTo(2);
        assertThat(states.disabled).containsEntry(1, 402);

        // 503 (yoğun): anahtarla ilgisi yok; kapatılmaz, hata sağlayıcı yönlendiricisine gider
        assertThatThrownBy(() -> ring.call(key -> {
            throw new RateLimitedException("Gemini TTS", 30, 503);
        })).isInstanceOf(RateLimitedException.class);
        assertThat(states.disabled).doesNotContainKey(2);
        // Diğer hatalar da anahtarı kapatmaz
        assertThatThrownBy(() -> ring.call(key -> {
            throw new IllegalStateException("boş yanıt");
        })).hasMessage("boş yanıt");
        assertThat(states.disabled).hasSize(1);
    }

    @Test
    void whenAllKeysFailOrAreClosedProviderIsSkipped() {
        assertThatThrownBy(() -> ring.call(key -> {
            throw new RateLimitedException("Gemini TTS", 30);
        })).isInstanceOf(RateLimitedException.class);
        assertThat(states.disabled).containsOnlyKeys(1, 2, 3);
        assertThat(ring.hasUsable()).isFalse();
        assertThatThrownBy(() -> ring.call(ApiCredential::secret))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("açık API anahtarı yok");
    }

    @Test
    void credentialNeverPrintsTheSecret() {
        ApiCredential key = new ApiCredential(2, "GROQ_API_KEY_2", "gsk_cok-gizli-anahtar-9876", null, null);
        assertThat(key.masked()).isEqualTo("••••9876");
        assertThat(key.toString()).doesNotContain("gizli").contains("GROQ_API_KEY_2");
    }
}
