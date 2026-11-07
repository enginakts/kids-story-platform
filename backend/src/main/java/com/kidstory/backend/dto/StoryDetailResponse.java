package com.kidstory.backend.dto;

import java.util.List;

/** Görsel prompt'ları ve üretim ayrıntıları ebeveyne gösterilmez. */
public record StoryDetailResponse(StorySummaryResponse story, String narrationStyle, List<Scene> scenes) {

    public record Scene(int sceneNumber, String text, String imageUrl, String audioUrl) {
    }
}
