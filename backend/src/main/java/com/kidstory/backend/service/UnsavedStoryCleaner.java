package com.kidstory.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

/**
 * Kaydedilmeyen (geçici) masalları süresi dolunca temizler (V16): masal satırı (sahneler, işler vb. bağlı kurallarla)
 * ve diskteki klasörü silinir. Süre app.stories.unsaved-ttl-hours, sıklık app.stories.cleanup-interval-ms (varsayılan 1 saat).
 * Okuma tarafında süresi dolmuş geçici masal zaten "bulunamadı" sayılır; bu görev yalnızca yer açar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnsavedStoryCleaner {

    private static final int BATCH = 200;

    private final JdbcTemplate jdbc;
    private final MediaStorage storage;

    @Value("${app.stories.cleanup-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.stories.cleanup-interval-ms:3600000}", initialDelayString = "${app.stories.cleanup-initial-delay-ms:120000}")
    public void scheduled() {
        if (enabled) {
            runOnce();
        }
    }

    /** Süresi dolmuş geçici masalları siler; silinen masal sayısını döner. Testler doğrudan çağırır. */
    public int runOnce() {
        List<Long> expired = jdbc.queryForList(
                "SELECT id FROM stories WHERE saved_at IS NULL AND created_at < ? ORDER BY created_at LIMIT ?",
                Long.class, Timestamp.from(StoryRetention.cutoff()), BATCH);
        int deleted = 0;
        for (Long id : expired) {
            // Arada kaydedildiyse dokunulmaz
            if (jdbc.update("DELETE FROM stories WHERE id = ? AND saved_at IS NULL", id) == 1) {
                storage.deleteFolder("stories/" + id);
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("Kaydedilmeyen {} geçici masal temizlendi", deleted);
        }
        return deleted;
    }
}
