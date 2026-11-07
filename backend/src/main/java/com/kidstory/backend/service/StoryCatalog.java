package com.kidstory.backend.service;

import com.kidstory.backend.entity.StoryEnums;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Masal seçeneklerinin Türkçe etiketleri (arayüz ve prompt). */
public final class StoryCatalog {

    private StoryCatalog() {
    }

    private static final Map<StoryEnums.Theme, String> THEMES = Map.of(
            StoryEnums.Theme.FRIENDSHIP, "Dostluk",
            StoryEnums.Theme.HELPING, "Yardımlaşma",
            StoryEnums.Theme.SHARING, "Paylaşma",
            StoryEnums.Theme.NATURE_LOVE, "Doğa sevgisi",
            StoryEnums.Theme.EMOTIONS, "Duyguları tanıma",
            StoryEnums.Theme.COURAGE, "Cesaret",
            StoryEnums.Theme.SELF_CARE, "Kişisel bakım",
            StoryEnums.Theme.DAILY_HABITS, "Günlük alışkanlıklar");

    private static final Map<StoryEnums.NarrationStyle, String> STYLES = Map.of(
            StoryEnums.NarrationStyle.WARM, "Sıcak ve sakin",
            StoryEnums.NarrationStyle.RHYMING, "Kafiyeli",
            StoryEnums.NarrationStyle.PLAYFUL, "Neşeli ve oyunlu",
            StoryEnums.NarrationStyle.BEDTIME, "Uyku masalı");

    private static final Map<StoryEnums.Length, String> LENGTHS = Map.of(
            StoryEnums.Length.SHORT, "Kısa (4 sahne)",
            StoryEnums.Length.MEDIUM, "Orta (5 sahne)",
            StoryEnums.Length.LONG, "Uzun (6 sahne)");

    private static final Map<StoryEnums.Status, String> STATUSES = Map.of(
            StoryEnums.Status.PENDING, "Sırada",
            StoryEnums.Status.GENERATING, "Hazırlanıyor",
            StoryEnums.Status.COMPLETED, "Hazır",
            StoryEnums.Status.FAILED, "Oluşturulamadı");

    public static String theme(StoryEnums.Theme theme) {
        return THEMES.get(theme);
    }

    public static String style(StoryEnums.NarrationStyle style) {
        return STYLES.get(style);
    }

    public static String length(StoryEnums.Length length) {
        return LENGTHS.get(length);
    }

    public static String status(StoryEnums.Status status) {
        return STATUSES.get(status);
    }

    public record Choice(String code, String label) {
    }

    public static List<Choice> themes() {
        return Arrays.stream(StoryEnums.Theme.values()).map(t -> new Choice(t.name(), theme(t))).toList();
    }

    public static List<Choice> styles() {
        return Arrays.stream(StoryEnums.NarrationStyle.values()).map(s -> new Choice(s.name(), style(s))).toList();
    }

    public static List<Choice> lengths() {
        return Arrays.stream(StoryEnums.Length.values()).map(l -> new Choice(l.name(), length(l))).toList();
    }
}
