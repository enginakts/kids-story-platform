package com.kidstory.backend.dto;

import com.kidstory.backend.entity.Story;
import com.kidstory.backend.service.StoryCatalog;

import java.time.Instant;

public record StorySummaryResponse(Long id, String title, String status, String statusLabel, String theme,
                                   String childName, int totalScenes, Instant createdAt, boolean audioReady,
                                   boolean coverReady) {

    public static StorySummaryResponse from(Story s) {
        return from(s, false);
    }

    /**
     * audioReady: tüm sahnelerin sesi hazır. coverReady: kapak (1. sahnenin resmi) hazır; mobil API'de
     * /api/v1/stories/{id}/scenes/1/image adresinden alınır.
     */
    public static StorySummaryResponse from(Story s, boolean audioReady) {
        return new StorySummaryResponse(s.getId(), s.getTitle(), s.getStatus().name(), StoryCatalog.status(s.getStatus()),
                StoryCatalog.theme(s.getTheme()), s.getChild().getName(), s.getTotalScenes(), s.getCreatedAt(), audioReady,
                s.getCoverImageUrl() != null);
    }
}
