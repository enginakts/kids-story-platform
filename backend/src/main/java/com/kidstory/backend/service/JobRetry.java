package com.kidstory.backend.service;

import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.service.ai.RateLimitedException;

import java.time.Duration;
import java.time.Instant;

/** Üretim işlerinin ortak hata kaydı ve yeniden deneme zamanlaması. */
final class JobRetry {

    private JobRetry() {
    }

    /**
     * Hatayı işe yazar. Deneme hakkı varsa işi yeniden kuyruğa koyar ve true döner; yoksa FAILED yapar.
     * AiRouter sağlayıcı hatasını sarar; asıl neden ve hız sınırı zincirin içinden bulunur.
     */
    static boolean reschedule(GenerationJob job, Exception error, long baseDelaySeconds) {
        Throwable root = error;
        RateLimitedException rateLimited = null;
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RateLimitedException r) {
                rateLimited = r;
            }
            root = t;
        }
        String message = message(root);
        job.setLastError(message.length() > 500 ? message.substring(0, 500) : message);
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            long delay = rateLimited != null ? rateLimited.getRetryAfterSeconds() : baseDelaySeconds * job.getAttemptCount();
            job.setStatus(GenerationJob.Status.QUEUED);
            job.setRunAfter(Instant.now().plus(Duration.ofSeconds(delay)));
            return true;
        }
        job.setStatus(GenerationJob.Status.FAILED);
        job.setFinishedAt(Instant.now());
        return false;
    }

    static String message(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
