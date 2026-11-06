package com.kidstory.backend.config;

import com.kidstory.backend.service.story.GenerationWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Üretim kuyruğunu düzenli aralıkla işler. Testlerde kapalıdır; testler kuyruğu elle çalıştırır. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {

    private final GenerationWorker worker;

    SchedulingConfig(GenerationWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-ms:2000}")
    void pollGenerationQueue() {
        worker.runPending();
    }
}
