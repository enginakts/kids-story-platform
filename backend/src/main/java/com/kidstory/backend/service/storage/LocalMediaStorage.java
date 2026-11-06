package com.kidstory.backend.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Dosyaları yerel diskte tutar. Tek sunucu ve geliştirme içindir. */
@Component
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    LocalMediaStorage(@Value("${app.storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void save(String key, byte[] data) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "upload", ".tmp");
            Files.write(temp, data);
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<Resource> load(String key) {
        Path path = resolve(key);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    @Override
    public void deletePrefix(String prefix) {
        try {
            FileSystemUtils.deleteRecursively(resolve(prefix));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Anahtar kök dizinin dışına çıkamaz. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Geçersiz dosya anahtarı");
        }
        return path;
    }
}
