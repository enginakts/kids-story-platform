package com.kidstory.backend.service;

import com.kidstory.backend.exception.UnsafeContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.stream.Collectors;

/** LLM çıktılarını ayrıştırır ve kullanıcıya ulaşmadan önce biçim ve içerik güvenliği denetiminden geçirir. */
@Component
@RequiredArgsConstructor
public class StoryDraftParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MIN_SCENES = 4;
    static final int MAX_SCENES = 6;

    private final ContentFilterService contentFilter;

    /** 1. aşama: ilk satır başlık, kalanı masal metni. */
    public StoryText parseText(String raw, boolean childAllowed, String placeholder) {
        String text = raw.replace("\r\n", "\n").strip();
        int newline = text.indexOf('\n');
        if (newline < 0) {
            throw new UnsafeContentException("Masal metni başlık ve gövde içermiyor");
        }
        String title = text.substring(0, newline).replaceAll("^[#*\\s]*(Başlık\\s*:)?\\s*", "")
                .replaceAll("[*\"“”]", "").strip();
        String body = Arrays.stream(text.substring(newline + 1).split("\n"))
                .map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.joining("\n\n"));
        if (title.isEmpty() || title.length() > 120) {
            throw new UnsafeContentException("Başlık geçersiz");
        }
        if (body.length() < 40 || body.length() > 5000) {
            throw new UnsafeContentException("Masal metni uzunluğu geçersiz");
        }
        check(title, childAllowed, placeholder);
        check(body, childAllowed, placeholder);
        return new StoryText(title, body);
    }

    /** 2. aşama: sahnelere bölünmüş JSON. */
    public StoryDraft parse(String raw, boolean childAllowed, String placeholder) {
        StoryDraft draft;
        try {
            draft = JSON.readValue(raw, StoryDraft.class);
        } catch (JacksonException e) {
            throw new UnsafeContentException("Yanıt geçerli JSON değil");
        }
        if (draft.title() == null || draft.title().isBlank() || draft.title().length() > 120) {
            throw new UnsafeContentException("Başlık geçersiz");
        }
        if (draft.scenes() == null || draft.scenes().size() < MIN_SCENES || draft.scenes().size() > MAX_SCENES) {
            throw new UnsafeContentException("Sahne sayısı 4-6 aralığında değil");
        }
        check(draft.title(), childAllowed, placeholder);
        for (StoryDraft.Scene scene : draft.scenes()) {
            if (scene.text() == null || scene.text().isBlank() || scene.text().length() > 400) {
                throw new UnsafeContentException("Sahne metni geçersiz");
            }
            if (scene.imagePrompt() == null || scene.imagePrompt().isBlank() || scene.imagePrompt().length() > 800) {
                throw new UnsafeContentException("Görsel tarifi geçersiz");
            }
            check(scene.text(), childAllowed, placeholder);
        }
        return draft;
    }

    private void check(String text, boolean childAllowed, String placeholder) {
        contentFilter.findBlockedWord(text).ifPresent(word -> {
            throw new UnsafeContentException("Kara listedeki ifade: " + word);
        });
        if (!childAllowed && ChildNamePersonalizer.mentions(text, placeholder)) {
            throw new UnsafeContentException("Çocuk istenmediği hâlde masalda yer aldı");
        }
    }
}
