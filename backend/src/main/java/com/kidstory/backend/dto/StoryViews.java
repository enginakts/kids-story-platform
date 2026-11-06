package com.kidstory.backend.dto;

import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.LocationBible;
import com.kidstory.backend.entity.StoryEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Masal ekranları ve REST API için salt okunur görünümler. Lazy ilişkiler işlem içinde
 * düz değerlere çevrilir; şablonlar entity'ye dokunmaz.
 */
public final class StoryViews {

    private StoryViews() {
    }

    public record Summary(Long id, Long childId, String childName, String title, StoryEnums.Status status,
                          String themeLabel, boolean hasCover, Instant createdAt) {
    }

    public record Scene(int number, String text, boolean hasImage, boolean hasAudio, BigDecimal audioDurationSeconds,
                        String timestampsJson) {
    }

    public record Detail(Long id, Long childId, String childName, String title, StoryEnums.Status status,
                         String themeLabel, String situationLabel, String emotionLabel, String narrationLabel,
                         String characterName, List<String> sideCharacterNames, String locationName,
                         int totalScenes, int progressPercent, boolean hasCover, Instant createdAt, List<Scene> scenes) {

        public boolean inProgress() {
            return status == StoryEnums.Status.PENDING || status == StoryEnums.Status.GENERATING;
        }
    }

    /** Hazır karakterlerin masal kitabı illüstrasyonu (static/img/landing); özel karakterlerde yoktur. */
    private static final java.util.Map<String, String> PRESET_IMAGES = java.util.Map.of(
            "Pamuk", "pamuk", "Leo", "leo", "Fikri", "fikri", "Mavi", "mavi", "Fındık", "findik", "Bal", "bal");

    public record CharacterOption(Long id, String name, String species, String personality, boolean childSpecific,
                                  String imageKey) {
        public static CharacterOption from(CharacterBible c) {
            return new CharacterOption(c.getId(), c.getName(), c.getSpecies(), c.getPersonalityTraits(), !c.isSystemPreset(),
                    c.isSystemPreset() ? PRESET_IMAGES.get(c.getName()) : null);
        }
    }

    /** Hazır mekânların vektör ikonu (static/img/art.svg). */
    private static final java.util.Map<String, String> PLACE_ICONS = java.util.Map.of(
            "Çiçekli Orman", "tree", "Bulut Şatosu", "castle", "Kumsal Köyü", "shell",
            "Bahçeli Ev", "house", "Renkli Anaokulu", "school", "Yıldızlı Oda", "bed");

    public record LocationOption(Long id, String name, String environment, String atmosphere, String imageKey) {
        public static LocationOption from(LocationBible l) {
            return new LocationOption(l.getId(), l.getName(), l.getEnvironmentType(), l.getAtmosphereDescription(),
                    PLACE_ICONS.getOrDefault(l.getName(), "star"));
        }
    }

    public record EnumOption(String value, String label) {
        public static <E extends Enum<E> & StoryEnums.Labeled> List<EnumOption> of(Class<E> type) {
            return Arrays.stream(type.getEnumConstants()).map(e -> new EnumOption(e.name(), e.getLabel())).toList();
        }
    }

    /** Sihirbazın tüm seçenekleri; mobil istemci de aynı listeyi kullanır. */
    public record Options(List<CharacterOption> characters, List<LocationOption> locations, List<EnumOption> themes,
                          List<EnumOption> situations, List<EnumOption> emotions, List<EnumOption> narrationStyles) {
    }
}
