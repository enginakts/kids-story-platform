package com.kidstory.backend.service;

import org.springframework.core.io.Resource;

import java.util.Optional;

/**
 * Masal ses ve görsel dosyaları. Anahtar ("stories/12/scene-1-ab12.mp3") veritabanında saklanır; dosyalar
 * yalnızca sahiplik denetimi yapan uç noktalardan sunulur. Bulut adımında S3 uyumlu bir uygulama eklenir.
 */
public interface MediaStorage {

    void store(String key, byte[] data);

    Optional<Resource> load(String key);

    void delete(String key);
}
