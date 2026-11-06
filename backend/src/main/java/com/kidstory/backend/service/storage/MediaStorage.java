package com.kidstory.backend.service.storage;

import org.springframework.core.io.Resource;

import java.util.Optional;

/**
 * Üretilen görsel ve ses dosyaları. Anahtarlar yalnızca sunucuda üretilir (ör. stories/12/scene-1.png);
 * dosyalar herkese açık adresle değil, sahiplik denetimi yapan controller üzerinden sunulur.
 * Üretimde S3 veya Cloudflare R2 adaptörü bu arayüzü uygular.
 */
public interface MediaStorage {

    void save(String key, byte[] data);

    Optional<Resource> load(String key);

    void deletePrefix(String prefix);
}
