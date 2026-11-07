package com.kidstory.backend.dto;

import com.kidstory.backend.entity.Story;
import com.kidstory.backend.service.StoryCatalog;

import java.time.Instant;

public record StorySummaryResponse(Long id, String title, String status, String statusLabel, String theme,
                                   String childName, int totalScenes, Instant createdAt) {

    public static StorySummaryResponse from(Story s) {
        return new StorySummaryResponse(s.getId(), s.getTitle(), s.getStatus().name(), StoryCatalog.status(s.getStatus()),
                StoryCatalog.theme(s.getTheme()), s.getChild().getName(), s.getTotalScenes(), s.getCreatedAt());
    }
}
