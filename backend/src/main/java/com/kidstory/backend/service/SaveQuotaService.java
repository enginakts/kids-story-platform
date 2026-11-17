package com.kidstory.backend.service;

import org.springframework.stereotype.Service;

/**
 * "Bu masalı kaydet" öncesi kota denetimi. Kaydetme kotası ve bellek sayacı bir sonraki adımda ayrıntılandırılacak;
 * şimdilik her kayda izin verir. Denetim yalnızca sunucuda yapılır; reddederken ConflictException fırlatılmalıdır
 * (kullanıcıya mesajı gösterilir).
 */
@Service
public class SaveQuotaService {

    public void ensureCanSave(Long parentId) {
        // Bir sonraki adım: kayıtlı masal/bellek kotası kontrolü.
    }
}
