package com.kidstory.backend.service.story;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * LLM yanıtını şemaya göre doğrular. Geçersiz yanıt InvalidDraftException atar; iş yeniden denenir.
 */
public final class StoryDraftParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MAX_TITLE = 120;
    static final int MAX_SCENE_TEXT = 300;
    static final int MAX_IMAGE_PROMPT = 400;

    private StoryDraftParser() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Draft(String title, List<DraftScene> scenes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DraftScene(String text, String imagePrompt) {
    }

    public static class InvalidDraftException extends RuntimeException {
        public InvalidDraftException(String message) {
            super("Geçersiz masal yanıtı: " + message);
        }
    }

    /** Model JSON'u ```json bloğu veya açıklama metniyle sarabilir; yalnızca ilk { ile son } arası okunur. */
    public static Draft parse(String raw) {
        if (raw == null) {
            throw new InvalidDraftException("boş");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidDraftException("JSON bulunamadı");
        }
        Draft draft;
        try {
            draft = JSON.readValue(raw.substring(start, end + 1), Draft.class);
        } catch (JacksonException e) {
            throw new InvalidDraftException("JSON okunamadı");
        }
        validate(draft);
        return draft;
    }

    private static void validate(Draft d) {
        if (d.title() == null || d.title().isBlank() || d.title().length() > MAX_TITLE) {
            throw new InvalidDraftException("başlık");
        }
        if (d.scenes() == null || d.scenes().size() < 4 || d.scenes().size() > 6) {
            throw new InvalidDraftException("sahne sayısı 4-6 olmalı");
        }
        for (DraftScene s : d.scenes()) {
            if (s == null || s.text() == null || s.text().isBlank() || s.text().length() > MAX_SCENE_TEXT) {
                throw new InvalidDraftException("sahne metni");
            }
            if (s.imagePrompt() != null && s.imagePrompt().length() > MAX_IMAGE_PROMPT) {
                throw new InvalidDraftException("görsel tanımı çok uzun");
            }
        }
    }
}
