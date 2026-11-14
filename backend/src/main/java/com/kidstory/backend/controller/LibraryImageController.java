package com.kidstory.backend.controller;

import com.kidstory.backend.service.LibraryImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

/**
 * Karakter ve mekân örnek resimleri. Yöneticinin yüklediği, kişisel veri içermeyen kütüphane resimleridir;
 * ebeveyn sihirbazı, yönetim paneli ve mobil uygulama kullanır, bu yüzden oturum istemez (SecurityConfig).
 * Bir kimliğin içeriği hiç değişmez (yeni resim yeni kimlik alır); uzun süre önbelleğe alınabilir.
 */
@Controller
@RequiredArgsConstructor
public class LibraryImageController {

    private final LibraryImageService images;

    @GetMapping("/library/images/{id}")
    ResponseEntity<Resource> image(@PathVariable long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(images.load(id).resource());
    }
}
