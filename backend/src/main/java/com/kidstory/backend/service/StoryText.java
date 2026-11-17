package com.kidstory.backend.service;

import java.util.List;

/**
 * 1. aşama çıktısı: başlık ve masal metni (çocuğun adı yerine yer tutucu). titleCandidates, modelin aynı çağrıda yazdığı
 * başlık adaylarıdır (V17); title, sunucunun seçtiği adaydır (StoryTitles).
 */
public record StoryText(String title, String body, List<String> titleCandidates) {

    public StoryText {
        titleCandidates = titleCandidates == null || titleCandidates.isEmpty() ? List.of(title) : List.copyOf(titleCandidates);
    }

    public StoryText(String title, String body) {
        this(title, body, List.of(title));
    }
}
