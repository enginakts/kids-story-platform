package com.kidstory.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Dosyaları sunucunun diskinde tutar (app.media.dir). Yerel geliştirme ve tek sunuculu kurulum için. */
@Slf4j
@Component
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    public LocalMediaStorage(@Value("${app.media.dir:media}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Medya dosyaları: {}", root);
    }

    @Override
    public void store(String key, byte[] data) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Dosya kaydedilemedi: " + key, e);
        }
    }

    @Override
    public Optional<Resource> load(String key) {
        Path path = resolve(key);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Dosya silinemedi: {}", key);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Geçersiz dosya anahtarı");
        }
        return path;
    }
}
