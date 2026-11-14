package com.kidstory.backend.service;

import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.entity.LocationBible;
import com.kidstory.backend.entity.PromptTemplate;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.service.ai.LlmRequest;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * İki aşamalı masal prompt'ları:
 * 1) Trendyol LLM masal metnini yazar (yaş, tema, karakterler, ilgi alanları).
 * 2) Gemini metni sahnelere böler ve görsel tariflerini yazar (yalnızca metin ve görünüş tanımları).
 * Çocuğun adı, cinsiyeti ve kimliği hiçbir aşamaya girmez; çocuk masalda yer alacaksa yer tutucu ad kullanılır.
 */
public final class StoryPromptBuilder {

    public static final String TEXT_TEMPLATE = "story.text.v3";
    public static final String SCENE_TEMPLATE = "story.scenes.v1";
    public static final String STYLE = "soft watercolor, whimsical children's book illustration, gentle pastel colors, clean lighting, no text";
    private static final List<String> PLACEHOLDERS = List.of("Kiko", "Lumo", "Pofi", "Tuti");

    private StoryPromptBuilder() {
    }

    /** Masaldaki karakter ve mekân adlarıyla çakışmayan yer tutucu seçilir (her çağrıda aynı sonucu verir). */
    public static String placeholder(Story story, List<CharacterBible> sides) {
        String names = Stream.concat(Stream.of(story.getPrimaryCharacter()), sides.stream())
                .map(CharacterBible::getName)
                .collect(Collectors.joining(" ")) + " " + story.getPrimaryLocation().getName();
        return PLACEHOLDERS.stream().filter(p -> !ContentFilterService.containsWord(names, p)).findFirst().orElse("Kiko");
    }

    public static LlmRequest text(PromptTemplate template, Story story, List<CharacterBible> sides) {
        ChildProfile child = story.getChild();
        String placeholder = placeholder(story, sides);
        String childLine = story.isPersonalizedUsed()
                ? "Çocuk: Masalda " + placeholder + " adında " + child.getAge() + " yaşında bir çocuk da ana karakterle birlikte yer alsın. "
                  + "Adını her zaman tam olarak \"" + placeholder + "\" yaz; ekleri kesme işaretiyle ekle (" + placeholder
                  + "'nun, " + placeholder + "'ya gibi)."
                : "Çocuk: Masalda insan çocuk karakter olmasın; yalnızca verilen karakterler yer alsın.";

        Map<String, String> values = Map.of(
                "age", String.valueOf(child.getAge()),
                "theme", StoryCatalog.theme(story.getTheme()),
                "narrationStyle", StoryCatalog.style(story.getNarrationStyle()),
                "scenes", String.valueOf(story.getTotalScenes()),
                "mainCharacter", describe(story.getPrimaryCharacter()),
                "sideCharacters", sides.isEmpty() ? "yok" : sides.stream().map(StoryPromptBuilder::describe).collect(Collectors.joining("; ")),
                "location", story.getPrimaryLocation().getName() + " (" + story.getPrimaryLocation().getAtmosphereDescription() + ")",
                "childLine", childLine,
                "interests", interests(child));
        return new LlmRequest(fill(template.getSystemPrompt(), values), fill(template.getUserPromptTemplate(), values), null, null);
    }

    public static LlmRequest scenes(PromptTemplate template, Story story, List<CharacterBible> sides, StoryText text) {
        String cast = Stream.concat(Stream.of(story.getPrimaryCharacter()), sides.stream())
                .map(c -> c.getName() + ": " + c.getVisualAnchorTokens())
                .collect(Collectors.joining("; "));
        Map<String, String> values = Map.of(
                "scenes", String.valueOf(story.getTotalScenes()),
                "placeholder", placeholder(story, sides),
                "cast", cast,
                "setting", setting(story.getPrimaryLocation()),
                "title", text.title(),
                "story", text.body());
        return new LlmRequest(fill(template.getSystemPrompt(), values), fill(template.getUserPromptTemplate(), values),
                StoryDraft.SCHEMA_NAME, StoryDraft.SCENE_SCHEMA);
    }

    /** Görsel üretim (Adım 6) için sahne prompt'u: sahne tarifi + karakter ve mekân tanımları + sabit stil. */
    public static String imagePrompt(String scenePrompt, Story story, List<CharacterBible> sides, String placeholder) {
        String scene = scenePrompt.replaceAll("(?<!\\p{L})" + Pattern.quote(placeholder) + "(['’]\\p{L}+)?", "a small child");
        String cast = Stream.concat(Stream.of(story.getPrimaryCharacter()), sides.stream())
                .map(CharacterBible::getVisualAnchorTokens)
                .collect(Collectors.joining("; "));
        return scene.trim() + ". Characters: " + cast + ". Setting: " + setting(story.getPrimaryLocation())
                + ". Style: " + STYLE;
    }

    /** Mekânın yönetim panelinde girilen çevre bilgisi: resim tarifi, ışık ve her sahnede görünen sabit öğeler. */
    static String setting(LocationBible l) {
        StringBuilder out = new StringBuilder(l.getVisualAnchorTokens().trim());
        if (l.getLightingStyle() != null && !l.getLightingStyle().isBlank()) {
            out.append("; lighting: ").append(l.getLightingStyle().trim());
        }
        if (l.getRecurringLandmarks() != null && !l.getRecurringLandmarks().isBlank()) {
            out.append("; recurring landmarks: ").append(l.getRecurringLandmarks().trim());
        }
        return out.toString();
    }

    private static String describe(CharacterBible c) {
        String personality = c.getPersonalityTraits() == null ? "" : "; kişilik: " + c.getPersonalityTraits();
        String look = c.getClothingDescription() == null ? "" : "; kıyafet: " + c.getClothingDescription();
        return c.getName() + " (" + c.getSpecies() + look + personality + ")";
    }

    private static String interests(ChildProfile child) {
        String joined = Stream.of(
                        label("sevdiği hayvanlar", child.getFavoriteAnimals()),
                        label("sevdiği renkler", child.getFavoriteColors()),
                        label("ilgi alanları", child.getSpecialInterests()))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("; "));
        return joined.isEmpty() ? "" : "İsteğe bağlı ilham (çocuğun sevdikleri): " + joined;
    }

    private static String label(String label, String value) {
        return value == null || value.isBlank() ? "" : label + ": " + value.trim();
    }

    private static String fill(String template, Map<String, String> values) {
        String out = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return out;
    }
}
