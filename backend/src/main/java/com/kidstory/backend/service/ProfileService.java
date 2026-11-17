package com.kidstory.backend.service;

import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.RefreshTokenRepository;
import com.kidstory.backend.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ebeveyn profili (/app/profile): hesap bilgileri, parola değiştirme, veri dışa aktarma (KVKK) ve hesabı silme.
 * Hesap silme tek bir veritabanı işlemidir: ebeveyn satırı silinince bağlı her şey veritabanı kuralıyla (ON DELETE CASCADE)
 * silinir: çocuk profilleri, masallar, sahneler, üretim işleri, cüzdan ve kredi geçmişi, oturum ve doğrulama tokenları,
 * onay kayıtları, ebeveynin kendi karakterleri ve sohbet kayıtları. Dosyalar (görsel, ses, karakter portresi) işlem
 * başarıyla bittikten sonra diskten silinir; işlem geri alınırsa hiçbir dosyaya dokunulmaz.
 * Yapay zekâ kullanım kayıtları (ai_usage_events) kişisel veri içermez; masal bağlantısı kopar ve anonim kalır (karar).
 * Yayımlanmış masallar (Keşfet) silinmez, sahipsiz kalır (V15): çocuğun adı metinlerde nötr bir adla değiştirilir ve
 * adı içeren seslendirmeler silinir; sahne resimleri kalır (çocuk resimlerde zaten adsız "a small child" olarak çizilir).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    static final Locale TR = Locale.forLanguageTag("tr");
    private static final String DELETED = "[silinmiş hesap]";
    /** Korunan yayımlanmış masallarda çocuğun adının yerine geçen nötr ad (ekler buna göre çekimlenir: Minik'in, Minik'e). */
    static final String NEUTRAL_NAME = "Minik";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final MediaStorage storage;

    public record Overview(String firstName, String lastName, String email, Instant memberSince, long children, long stories) {
    }

    @Transactional(readOnly = true)
    public Overview overview(Long parentId) {
        return jdbc.query("""
                SELECT p.first_name, p.last_name, p.email, p.created_at,
                       (SELECT count(*) FROM child_profiles c WHERE c.parent_id = p.id AND c.is_active) AS children,
                       (SELECT count(*) FROM stories s WHERE s.parent_id = p.id AND s.saved_at IS NOT NULL) AS stories
                FROM parents p WHERE p.id = ?""",
                rs -> {
                    if (!rs.next()) {
                        throw new NotFoundException("Hesap bulunamadı");
                    }
                    return new Overview(rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"),
                            rs.getTimestamp("created_at").toInstant(), rs.getLong("children"), rs.getLong("stories"));
                }, parentId);
    }

    /** Ad ve soyad (kayıttaki kurallar: boş olamaz, en fazla 100 karakter). */
    @Transactional
    public void updateName(Long parentId, String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.strip();
        String last = lastName == null ? "" : lastName.strip();
        if (first.isEmpty() || first.length() > 100) {
            throw new FieldValidationException("firstName", "Ad 1-100 karakter olmalıdır");
        }
        if (last.isEmpty() || last.length() > 100) {
            throw new FieldValidationException("lastName", "Soyad 1-100 karakter olmalıdır");
        }
        jdbc.update("UPDATE parents SET first_name = ?, last_name = ?, updated_at = now() WHERE id = ?", first, last, parentId);
    }

    /** Mevcut parola doğrulanır; yeni parola kayıttaki kurala uyar (8-72 karakter). Mobil oturumlar kapatılır. */
    @Transactional
    public void changePassword(Long parentId, String currentPassword, String newPassword) {
        if (!passwordMatches(parentId, currentPassword)) {
            throw new FieldValidationException("currentPassword", "Mevcut parola yanlış");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new FieldValidationException("newPassword", "Yeni parola 8-72 karakter olmalıdır");
        }
        if (passwordMatches(parentId, newPassword)) {
            throw new FieldValidationException("newPassword", "Yeni parola mevcut parolayla aynı olamaz");
        }
        jdbc.update("UPDATE parents SET password_hash = ?, updated_at = now() WHERE id = ?", passwordEncoder.encode(newPassword), parentId);
        refreshTokens.revokeAll(parentId, Instant.now());
    }

    /**
     * KVKK "verilerimi indir": hesap bilgisi, çocukların takma adı ve yaşı, masal başlıkları ve sahne metinleri.
     * Görseller, sesler ve iç kimlikler dahil edilmez.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(Long parentId) {
        Overview o = overview(parentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("olusturulma", OffsetDateTime.now().toString());
        out.put("hesap", new LinkedHashMap<>(Map.of("ad", o.firstName(), "soyad", o.lastName(), "eposta", o.email(),
                "uyelikTarihi", o.memberSince().toString())));
        List<Map<String, Object>> children = new ArrayList<>();
        jdbc.query("""
                SELECT id, name, age, favorite_animals, favorite_colors, special_interests
                FROM child_profiles WHERE parent_id = ? AND is_active ORDER BY created_at""", rs -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("takmaAd", rs.getString("name"));
            c.put("yas", rs.getInt("age"));
            c.put("sevdigiHayvanlar", rs.getString("favorite_animals"));
            c.put("sevdigiRenkler", rs.getString("favorite_colors"));
            c.put("ilgiAlanlari", rs.getString("special_interests"));
            c.put("masallar", stories(parentId, rs.getLong("id")));
            children.add(c);
        }, parentId);
        out.put("cocuklar", children);
        return out;
    }

    private List<Map<String, Object>> stories(Long parentId, long childId) {
        List<Map<String, Object>> list = new ArrayList<>();
        jdbc.query("""
                SELECT id, title, theme, created_at FROM stories
                WHERE parent_id = ? AND child_id = ? ORDER BY created_at""", rs -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("baslik", rs.getString("title"));
            s.put("tema", StoryCatalog.theme(com.kidstory.backend.entity.StoryEnums.Theme.valueOf(rs.getString("theme"))));
            s.put("tarih", rs.getTimestamp("created_at").toInstant().toString());
            s.put("sahneler", jdbc.queryForList(
                    "SELECT scene_text FROM story_scenes WHERE story_id = ? ORDER BY scene_number", String.class, rs.getLong("id")));
            list.add(s);
        }, parentId, childId);
        return list;
    }

    /**
     * Hesabı kalıcı olarak siler. confirmWord "SİL" olmalı (Türkçe klavyesi olmayanlar için "SIL" de kabul) ve parola doğru olmalı.
     * Hata olursa işlem geri alınır, hiçbir şey silinmez.
     */
    @Transactional
    public void deleteAccount(Long parentId, String confirmWord, String password) {
        String word = confirmWord == null ? "" : confirmWord.strip().toUpperCase(TR);
        if (!word.equals("SİL") && !word.equals("SIL")) {
            throw new FieldValidationException("confirmWord", "Onaylamak için SİL yazın");
        }
        if (!passwordMatches(parentId, password)) {
            throw new FieldValidationException("password", "Parola yanlış");
        }
        String email = jdbc.queryForObject("SELECT email FROM parents WHERE id = ?", String.class, parentId);

        // Silinecek dosya klasörleri: yayımlanmamış masallar (sahne görselleri, sesler, kapak),
        // ebeveynin karakterlerinin portreleri ve resimleri. Yayımlanmış masalların klasörü kalır (yalnızca sesleri silinir).
        List<String> folders = new ArrayList<>();
        jdbc.queryForList("SELECT id FROM stories WHERE parent_id = ? AND published_at IS NULL", Long.class, parentId)
                .forEach(id -> folders.add("stories/" + id));
        List<Long> characters = jdbc.queryForList("SELECT id FROM character_bibles WHERE parent_id = ?", Long.class, parentId);
        characters.forEach(id -> folders.add("characters/" + id));
        List<String> files = new ArrayList<>(characters.isEmpty() ? List.of() : jdbc.queryForList(
                "SELECT storage_key FROM library_images WHERE character_id = ANY (?)", String.class,
                (Object) characters.toArray(Long[]::new)));
        files.addAll(anonymizePublished(parentId));

        // Yönetim denetim kayıtlarında kalan e-posta adresi anonimleştirilir (kayıt yönetim geçmişi olarak kalır)
        jdbc.update("UPDATE admin_audit_logs SET details = replace(details, ?, ?) WHERE target_entity = 'parent' AND target_id = ?",
                email, DELETED, parentId);
        // Yayımlanmamış masallar açıkça silinir (sahneler, işler vb. masala bağlı kurallarla gider);
        // ebeveyn silinince yayımlanmış masalların ebeveyn ve çocuk bağlantısı boşa düşer (V15)
        jdbc.update("DELETE FROM stories WHERE parent_id = ? AND published_at IS NULL", parentId);
        int deleted = jdbc.update("DELETE FROM parents WHERE id = ?", parentId);
        if (deleted != 1) {
            throw new NotFoundException("Hesap bulunamadı");
        }
        loginAttempts.reset("parent", email);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                folders.forEach(storage::deleteFolder);
                files.forEach(storage::delete);
                log.info("Ebeveyn hesabı silindi (id={}, {} klasör, {} dosya)", parentId, folders.size(), files.size());
            }
        });
    }

    /**
     * Korunan yayımlanmış masallarda çocuğun adı (masal kişiselleştirildiyse) başlık ve metinlerde nötr adla değiştirilir;
     * seslendirmeler çocuğun adını sesli okuduğu için silinir ve bekleyen ses işleri iptal edilir.
     * Dönen liste, işlem bitince diskten silinecek ses dosyalarıdır. Yeni yapay zekâ çağrısı yapılmaz.
     */
    private List<String> anonymizePublished(Long parentId) {
        List<Map<String, Object>> published = jdbc.queryForList("""
                SELECT s.id, s.title, s.personalized_used, c.name AS child_name
                FROM stories s JOIN child_profiles c ON c.id = s.child_id
                WHERE s.parent_id = ? AND s.published_at IS NOT NULL""", parentId);
        List<String> audio = new ArrayList<>();
        for (Map<String, Object> row : published) {
            long storyId = ((Number) row.get("id")).longValue();
            String name = (String) row.get("child_name");
            if (Boolean.TRUE.equals(row.get("personalized_used")) && name != null && !name.isBlank()) {
                String n = name.strip();
                jdbc.update("UPDATE stories SET title = ? WHERE id = ?",
                        ChildNamePersonalizer.apply((String) row.get("title"), n, NEUTRAL_NAME), storyId);
                for (Map<String, Object> scene : jdbc.queryForList(
                        "SELECT id, scene_text, voice_text FROM story_scenes WHERE story_id = ?", storyId)) {
                    jdbc.update("UPDATE story_scenes SET scene_text = ?, voice_text = ? WHERE id = ?",
                            ChildNamePersonalizer.apply((String) scene.get("scene_text"), n, NEUTRAL_NAME),
                            ChildNamePersonalizer.apply((String) scene.get("voice_text"), n, NEUTRAL_NAME),
                            scene.get("id"));
                }
            }
            audio.addAll(jdbc.queryForList(
                    "SELECT audio_url FROM story_scenes WHERE story_id = ? AND audio_url IS NOT NULL", String.class, storyId));
            jdbc.update("UPDATE story_scenes SET audio_url = NULL, audio_duration_seconds = NULL, timestamps_json = NULL WHERE story_id = ?",
                    storyId);
            jdbc.update("DELETE FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO'", storyId);
        }
        return audio;
    }

    private boolean passwordMatches(Long parentId, String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        List<String> hash = jdbc.queryForList("SELECT password_hash FROM parents WHERE id = ?", String.class, parentId);
        return !hash.isEmpty() && passwordEncoder.matches(raw, hash.getFirst());
    }
}
