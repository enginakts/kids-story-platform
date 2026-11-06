package com.kidstory.backend.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Optional;

/** Masal dosyalarını özel önbellek başlığıyla sunar; paylaşılan önbelleklerde saklanmaz. */
final class MediaResponses {

    private MediaResponses() {
    }

    static ResponseEntity<Resource> of(Optional<Resource> resource) {
        return resource.map(r -> ResponseEntity.ok()
                        .contentType(MediaTypeFactory.getMediaType(r).orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(r))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
