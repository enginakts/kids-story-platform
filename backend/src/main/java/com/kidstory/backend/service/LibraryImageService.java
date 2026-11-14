package com.kidstory.backend.service;

import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Karakter ve mekânların örnek resimleri (library_images, V11). Yönetici yükler; ebeveyn sihirbazda görür.
 * Resimler yeniden kodlanmış JPEG olarak dosya deposunda tutulur ve /library/images/{id} adresinden herkese açık
 * sunulur (kişisel veri içermez).
 */
@Service
@RequiredArgsConstructor
public class LibraryImageService {

    public static final int MAX_IMAGES = 4;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int STORED_SIDE = 1200;

    private final JdbcTemplate jdbc;
    private final MediaStorage storage;

    /** Resmin sahibi: karakter ya da mekân. */
    public enum Kind {
        CHARACTER("character_id", "characters"), LOCATION("location_id", "locations");

        final String column;
        final String folder;

        Kind(String column, String folder) {
            this.column = column;
            this.folder = folder;
        }
    }

    /** Doğrulanmış ve yeniden kodlanmış, kaydedilmeye hazır resimler. */
    public record Prepared(List<byte[]> images) {
        public boolean isEmpty() {
            return images.isEmpty();
        }
    }

    public record StoredImage(Resource resource) {}

    public static String url(long imageId) {
        return "/library/images/" + imageId;
    }

    /**
     * Formdan gelen dosyaları doğrular ve yeniden kodlar. Boş dosya alanları (seçim yapılmamış) atlanır.
     * Hata varsa hiçbir şey kaydedilmeden "images" alanı için FieldValidationException fırlatır.
     */
    public Prepared prepare(List<MultipartFile> files) {
        List<byte[]> out = new ArrayList<>();
        if (files == null) {
            return new Prepared(out);
        }
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            String name = f.getOriginalFilename() == null ? "Resim" : f.getOriginalFilename();
            if (f.getSize() > MAX_BYTES) {
                throw new FieldValidationException("images", name + ": en fazla 5 MB olabilir");
            }
            try {
                out.add(Images.sampleJpeg(f.getBytes(), STORED_SIDE));
            } catch (IllegalArgumentException e) {
                throw new FieldValidationException("images", name + ": " + e.getMessage());
            } catch (IOException e) {
                throw new FieldValidationException("images", name + ": resim okunamadı");
            }
        }
        if (out.size() > MAX_IMAGES) {
            throw new FieldValidationException("images", "En fazla " + MAX_IMAGES + " örnek resim yükleyebilirsiniz");
        }
        return new Prepared(out);
    }

    /** Bir kaydın resimlerinin kimlikleri, gösterim sırasıyla. */
    public List<Long> ids(Kind kind, long ownerId) {
        return jdbc.queryForList("SELECT id FROM library_images WHERE " + kind.column + " = ? ORDER BY sort_order, id",
                Long.class, ownerId);
    }

    /** Birden çok kaydın resimleri (sahip kimliği → resim kimlikleri), listeler için tek sorgu. */
    public Map<Long, List<Long>> ids(Kind kind, Collection<Long> ownerIds) {
        Map<Long, List<Long>> out = new HashMap<>();
        if (ownerIds.isEmpty()) {
            return out;
        }
        String in = ownerIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.query("SELECT " + kind.column + ", id FROM library_images WHERE " + kind.column + " IN (" + in + ")"
                        + " ORDER BY sort_order, id",
                rs -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
                }, ownerIds.toArray());
        return out;
    }

    public static List<String> urls(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().map(LibraryImageService::url).toList();
    }

    /**
     * Resimleri ekler ve istenenleri kaldırır. Sonuçta en az bir, en fazla MAX_IMAGES resim kalmalıdır
     * (requireOne: yeni kayıt ya da zaten resmi olan kayıt). Dosyalar veritabanı satırından önce yazılır.
     */
    public void update(Kind kind, long ownerId, Prepared added, Collection<Long> removeIds, boolean requireOne) {
        List<Long> current = ids(kind, ownerId);
        List<Long> removing = removeIds == null ? List.of() : current.stream().filter(removeIds::contains).toList();
        int remaining = current.size() - removing.size() + added.images().size();
        if (remaining > MAX_IMAGES) {
            throw new FieldValidationException("images",
                    "En fazla " + MAX_IMAGES + " örnek resim olabilir; önce birini kaldırın");
        }
        if (requireOne && remaining < 1) {
            throw new FieldValidationException("images", "En az bir örnek resim gerekli");
        }
        for (Long id : removing) {
            String key = jdbc.queryForObject("SELECT storage_key FROM library_images WHERE id = ?", String.class, id);
            jdbc.update("DELETE FROM library_images WHERE id = ?", id);
            deleteAfterCommit(key);
        }
        int order = jdbc.queryForObject("SELECT coalesce(max(sort_order), -1) + 1 FROM library_images WHERE " + kind.column + " = ?",
                Integer.class, ownerId);
        for (byte[] image : added.images()) {
            String key = "library/" + kind.folder + "/" + ownerId + "/" + UUID.randomUUID().toString().substring(0, 12) + ".jpg";
            storage.store(key, image);
            jdbc.update("INSERT INTO library_images (" + kind.column + ", storage_key, sort_order) VALUES (?, ?, ?)",
                    ownerId, key, order++);
        }
    }

    /** Dosya, veritabanı işlemi başarıyla bitince silinir; işlem geri alınırsa resim kaybolmaz. */
    private void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.delete(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    public StoredImage load(long imageId) {
        List<String> keys = jdbc.queryForList("SELECT storage_key FROM library_images WHERE id = ?", String.class, imageId);
        if (keys.isEmpty()) {
            throw new NotFoundException("Resim bulunamadı");
        }
        return new StoredImage(storage.load(keys.getFirst()).orElseThrow(() -> new NotFoundException("Resim bulunamadı")));
    }
}
