package com.kidstory.backend.service.story;

import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.entity.LocationBible;
import com.kidstory.backend.entity.PromptTemplate;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryEnums;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Metin ve görsel prompt'larını kurar.
 * Çocuğun adı hiçbir dış servise gönderilmez; yalnızca yaşı ve ebeveynin girdiği (filtrelenmiş) ilgi alanları gider.
 * Görsel tutarlılığı: her sahnenin görsel prompt'u aynı stil sabiti, aynı karakter ve mekân rehberi tanımı
 * ve aynı seed ile kurulur; LLM yalnızca sahnedeki eylemi yazar.
 */
public final class StoryPrompts {

    public static final String TEMPLATE_KEY = "story.single_shot.v2";
    public static final String STYLE =
            "soft watercolor, whimsical children's book illustration, gentle pastel colors, clean lighting, no text";
    public static final String NEGATIVE =
            "no text, no watermark, no dark colors, no horror, no scary elements, no violence, no realistic photo";

    private StoryPrompts() {
    }

    public record TextPrompt(String system, String user) {
    }

    public static TextPrompt text(PromptTemplate template, Story story, ChildProfile child, CharacterBible main,
                                  List<CharacterBible> sides, LocationBible location, String interests) {
        Map<String, String> values = Map.of(
                "age", String.valueOf(child.getAge()),
                "theme", story.getTheme().getLabel(),
                "situation", label(story.getSituation()),
                "emotion", label(story.getTargetEmotion()),
                "character", describe(main),
                "sideCharacters", sides.isEmpty() ? "-" : sides.stream().map(StoryPrompts::describe).collect(Collectors.joining(", ")),
                "location", location.getName() + " (" + location.getAtmosphereDescription() + ")",
                "narrationStyle", story.getNarrationStyle().getLabel(),
                "interests", interests == null || interests.isBlank() ? "-" : interests,
                "scenes", String.valueOf(story.getTotalScenes()));
        String user = template.getUserPromptTemplate();
        for (Map.Entry<String, String> e : values.entrySet()) {
            user = user.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return new TextPrompt(template.getSystemPrompt(), user);
    }

    public static String image(CharacterBible main, List<CharacterBible> sides, LocationBible location, String action) {
        StringBuilder p = new StringBuilder(STYLE).append(". Main character: ").append(appearance(main));
        for (CharacterBible side : sides) {
            p.append(". Friend: ").append(appearance(side));
        }
        return p.append(". Setting: ").append(location.getVisualAnchorTokens())
                .append(". Scene: ").append(action).toString();
    }

    /** Aynı karakter her sahnede aynı seed ile çizilir; rehberde seed yoksa masala özgü sabit bir seed kullanılır. */
    public static long seed(CharacterBible main, Story story) {
        return main.getBaseSeed() != null ? main.getBaseSeed() : 7919L * story.getId();
    }

    /** Ebeveynin profilde yazdığı ilgi alanları; prompt'u şişirmemek için kısaltılır. */
    public static String interests(ChildProfile child) {
        String joined = Stream.of(child.getFavoriteAnimals(), child.getFavoriteColors(), child.getSpecialInterests())
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return joined.length() > 300 ? joined.substring(0, 300) : joined;
    }

    private static String describe(CharacterBible c) {
        return c.getName() + " (" + c.getSpecies()
                + (c.getPersonalityTraits() == null ? "" : "; " + c.getPersonalityTraits()) + ")";
    }

    private static String appearance(CharacterBible c) {
        return c.getVisualAnchorTokens() + (c.getClothingDescription() == null ? "" : ", wearing " + c.getClothingDescription());
    }

    private static String label(StoryEnums.Labeled value) {
        return value == null ? "-" : value.getLabel();
    }
}
