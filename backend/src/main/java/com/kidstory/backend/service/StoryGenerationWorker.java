package com.kidstory.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Veritabanı kuyruğundan (generation_jobs) iş alır. FOR UPDATE SKIP LOCKED sayesinde birden fazla sunucu
 * aynı işi iki kez almaz. Çöken sunucuda RUNNING kalan işler 10 dakika sonra kuyruğa geri döner.
 */
@Component
@RequiredArgsConstructor
public class StoryGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(StoryGenerationWorker.class);
    private static final int BATCH = 5;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final StoryGenerationService generation;
    private final StoryAudioService audio;
    private final StoryImageService images;

    /** false ise zamanlanmış çalışma durur (testler runOnce'ı doğrudan çağırır). */
    @Value("${app.generation.worker-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.generation.poll-ms:2000}", initialDelayString = "${app.generation.poll-ms:2000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        for (int i = 0; i < BATCH && runOnce(); i++) {
            // kuyrukta iş kaldıkça devam
        }
    }

    /** Bir iş alıp işler; kuyruk boşsa false döner. Testler doğrudan çağırır. */
    public boolean runOnce() {
        tx.executeWithoutResult(s -> jdbc.update("""
                UPDATE generation_jobs SET status = 'QUEUED', run_after = now()
                WHERE status = 'RUNNING' AND started_at < now() - interval '10 minutes'"""));
        // Öncelik: masal metni, sonra sahne resimleri, sonra sesler. Medya işleri birikse de yeni masal beklemez.
        List<Map<String, Object>> claimed = tx.execute(s -> jdbc.queryForList("""
                UPDATE generation_jobs SET status = 'RUNNING', started_at = now(), attempt_count = attempt_count + 1
                WHERE id = (SELECT id FROM generation_jobs
                            WHERE status = 'QUEUED' AND run_after <= now() AND job_type IN ('STORY_TEXT', 'SCENE_IMAGE', 'SCENE_AUDIO')
                            ORDER BY CASE job_type WHEN 'STORY_TEXT' THEN 0 WHEN 'SCENE_IMAGE' THEN 1 ELSE 2 END, run_after, id
                            LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING id, job_type"""));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        Long jobId = ((Number) claimed.getFirst().get("id")).longValue();
        String type = (String) claimed.getFirst().get("job_type");
        try {
            switch (type) {
                case "STORY_TEXT" -> {
                    StoryGenerationService.Prepared prepared = generation.prepare(jobId);
                    generation.complete(prepared, generation.generate(prepared));
                }
                case "SCENE_IMAGE" -> {
                    StoryImageService.Prepared prepared = images.prepare(jobId);
                    images.complete(prepared, images.generate(prepared));
                }
                default -> {
                    StoryAudioService.Prepared prepared = audio.prepare(jobId);
                    audio.complete(prepared, audio.synthesize(prepared));
                }
            }
        } catch (Exception e) {
            try {
                switch (type) {
                    case "STORY_TEXT" -> generation.fail(jobId, e);
                    case "SCENE_IMAGE" -> images.fail(jobId, e);
                    default -> audio.fail(jobId, e);
                }
            } catch (Exception inner) {
                log.error("Üretim işi hatası kaydedilemedi (job={})", jobId, inner);
            }
        }
        return true;
    }
}
