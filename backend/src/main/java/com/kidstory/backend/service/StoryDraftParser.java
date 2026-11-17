package com.kidstory.backend.service;

import com.kidstory.backend.exception.UnsafeContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** LLM çıktılarını ayrıştırır ve kullanıcıya ulaşmadan önce biçim ve içerik güvenliği denetiminden geçirir. */
@Component
@RequiredArgsConstructor
public class StoryDraftParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MIN_SCENES = 4;
    static final int MAX_SCENES = 6;
    /** Sahne metni: hedef 45-75 kelime (3-5 kısa cümle, prompt'ta). Alt sınır model biraz kısa yazınca masal düşmesin diye
     *  25'tir (ilk denemede 29 kelimelik sahne masalı düşürdü); üst sınır metnin kontrolsüz uzamasını önler. */
    static final int MIN_SCENE_WORDS = 25;
    static final int MAX_SCENE_WORDS = 100;
    static final int MAX_SCENE_CHARS = 800;

    private final ContentFilterService contentFilter;

    /** 1. aşama: ilk satır başlık, kalanı masal metni. */
    public StoryText parseText(String raw, boolean childAllowed, String placeholder) {
        String text = raw.replace("\r\n", "\n").strip();
        int newline = text.indexOf('\n');
        if (newline < 0) {
            throw new UnsafeContentException("Masal metni başlık ve gövde içermiyor");
        }
        String body = Arrays.stream(text.substring(newline + 1).split("\n"))
                .map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.joining("\n\n"));
        if (body.length() < 40 || body.length() > 8000) {
            throw new UnsafeContentException("Masal metni uzunluğu geçersiz");
        }
        check(body, childAllowed, placeholder);
        // İlk satır: "BAŞLIKLAR: a | b | c" (V17) ya da eski biçimde tek başlık. Güvenlik denetiminden geçmeyen aday elenir.
        List<String> candidates = new ArrayList<>();
        for (String c : text.substring(0, newline).replaceAll("^[#*\\s]*((BAŞLIKLAR|Başlıklar|Başlık|BAŞLIK)\\s*:)?\\s*", "").split("\\|")) {
            String title = c.replaceAll("^\\s*\\d+[.)]\\s*", "").replaceAll("[*\"“”]", "").strip();
            if (title.isEmpty() || title.length() > 120 || candidates.contains(title)) {
                continue;
            }
            if (contentFilter.findBlockedWord(title).isPresent()
                    || (!childAllowed && ChildNamePersonalizer.mentions(title, placeholder))) {
                continue;
            }
            candidates.add(title);
        }
        if (candidates.isEmpty()) {
            throw new UnsafeContentException("Başlık geçersiz");
        }
        return new StoryText(candidates.getFirst(), body, candidates);
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
            if (scene.text() == null || scene.text().isBlank() || scene.text().length() > MAX_SCENE_CHARS) {
                throw new UnsafeContentException("Sahne metni geçersiz");
            }
            int words = words(scene.text());
            if (words < MIN_SCENE_WORDS || words > MAX_SCENE_WORDS) {
                throw new UnsafeContentException("Sahne metni " + words + " kelime; " + MIN_SCENE_WORDS + "-" + MAX_SCENE_WORDS + " aralığında olmalı");
            }
            if (scene.imagePrompt() == null || scene.imagePrompt().isBlank() || scene.imagePrompt().length() > 800) {
                throw new UnsafeContentException("Görsel tarifi geçersiz");
            }
            check(scene.text(), childAllowed, placeholder);
        }
        return draft;
    }

    static int words(String text) {
        String t = text.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
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
