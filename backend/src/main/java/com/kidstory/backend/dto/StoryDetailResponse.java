package com.kidstory.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Görsel prompt'ları ve üretim ayrıntıları ebeveyne gösterilmez. */
public record StoryDetailResponse(StorySummaryResponse story, String narrationStyle, List<Scene> scenes,
                                  Progress audio, Progress images) {

    /**
     * imageUrl / audioUrl: sahnenin resmi veya sesi hazırsa mobil API'deki adresi (Authorization başlığıyla indirilir),
     * değilse null.
     */
    public record Scene(int sceneNumber, String text, String imageUrl, String audioUrl, BigDecimal audioDurationSeconds) {
    }

    /**
     * Sahne sesleri veya resimlerinin durumu. status: NONE (istenmedi), PREPARING (hazırlanıyor), READY (tümü hazır),
     * PARTIAL (bazı sahneler hazırlanamadı), FAILED (hiçbiri hazırlanamadı).
     */
    public record Progress(String status, int ready, int total) {

        public boolean preparing() {
            return "PREPARING".equals(status);
        }

        public boolean canRequest() {
            return "NONE".equals(status) || "PARTIAL".equals(status) || "FAILED".equals(status);
        }
    }
}
