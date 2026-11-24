package com.kidstory.backend.service.ai;

/** Anahtar durumlarının saklandığı yer (V22 api_key_states). Birim testlerinde ALWAYS: her anahtar açık, kayıt yok. */
public interface ApiKeyStates {

    /** Anahtar şu an kullanılabilir mi (yönetici kapatmamış, otomatik kapanmışsa yeniden açılma vakti gelmiş). */
    boolean usable(String service, int slot);

    /** Bir çağrı denemesi yapıldı (bugünkü kullanım sayacı). */
    void used(String service, int slot);

    /** Kota/yetki hatası: anahtarı otomatik kapat. */
    void autoDisable(String service, int slot, int status, String reason);

    ApiKeyStates ALWAYS = new ApiKeyStates() {
        @Override
        public boolean usable(String service, int slot) {
            return true;
        }

        @Override
        public void used(String service, int slot) {
        }

        @Override
        public void autoDisable(String service, int slot, int status, String reason) {
        }
    };
}
