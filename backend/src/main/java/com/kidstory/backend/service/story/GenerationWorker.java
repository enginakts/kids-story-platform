package com.kidstory.backend.service.story;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Veritabanı kuyruğu (generation_jobs). FOR UPDATE SKIP LOCKED sayesinde birden fazla sunucu
 * aynı işi iki kez almaz; Redis gerekmez. Çöken sunucunun yarım bıraktığı işler süre dolunca geri alınır.
 */
@Component
public class GenerationWorker {

    private static final String CLAIM = """
            UPDATE generation_jobs SET status = 'RUNNING', started_at = now(), attempt_count = attempt_count + 1
            WHERE id = (SELECT id FROM generation_jobs WHERE status = 'QUEUED' AND run_after <= now()
                        ORDER BY run_after, id LIMIT 1 FOR UPDATE SKIP LOCKED)
            RETURNING id""";

    private static final String RECOVER_STALE = """
            UPDATE generation_jobs SET status = 'QUEUED', run_after = now()
            WHERE status = 'RUNNING' AND started_at < now() - interval '10 minutes'""";

    private final JdbcTemplate jdbc;
    private final StoryGenerationService generation;

    GenerationWorker(JdbcTemplate jdbc, StoryGenerationService generation) {
        this.jdbc = jdbc;
        this.generation = generation;
    }

    /** Hazır olan tüm işleri sırayla çalıştırır ve kaç iş çalıştığını döner. */
    public int runPending() {
        jdbc.update(RECOVER_STALE);
        int processed = 0;
        for (Long id = claim(); id != null; id = claim()) {
            generation.process(id);
            processed++;
        }
        return processed;
    }

    private Long claim() {
        List<Long> ids = jdbc.queryForList(CLAIM, Long.class);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
