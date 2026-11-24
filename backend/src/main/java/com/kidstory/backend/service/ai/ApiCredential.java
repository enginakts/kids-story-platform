package com.kidstory.backend.service.ai;

/**
 * .env'deki tek bir API anahtarı. slot 1 birincil (GEMINI_API_KEY), 2..9 yedek (GEMINI_API_KEY_2 ...): her biri ayrı bir
 * hesabın ücretsiz kotası. account: Cloudflare hesap kimliği; baseUrl: Azure bölgesine göre adres (diğerlerinde null).
 * toString anahtarı göstermez.
 */
public record ApiCredential(int slot, String envName, String secret, String account, String baseUrl) {

    public static ApiCredential of(String secret) {
        return new ApiCredential(1, null, secret, null, null);
    }

    /** Panelde gösterim: yalnızca son 4 karakter. */
    public String masked() {
        if (secret == null || secret.length() < 8) {
            return "••••";
        }
        return "••••" + secret.substring(secret.length() - 4);
    }

    @Override
    public String toString() {
        return "ApiCredential[" + (envName == null ? "#" + slot : envName) + " " + masked() + "]";
    }
}
