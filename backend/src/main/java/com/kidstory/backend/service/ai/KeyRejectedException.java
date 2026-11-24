package com.kidstory.backend.service.ai;

import lombok.Getter;

/**
 * Anahtar reddedildi: 401/403 (geçersiz ya da yetkisiz anahtar) veya 402 (kota/kredi bitti). Anahtar halkası bu
 * anahtarı otomatik kapatır ve aynı servisin yedek anahtarına geçer.
 */
@Getter
public class KeyRejectedException extends RuntimeException {

    private final int status;

    public KeyRejectedException(String message, int status) {
        super(message);
        this.status = status;
    }

    public static boolean rejects(int status) {
        return status == 401 || status == 402 || status == 403;
    }
}
