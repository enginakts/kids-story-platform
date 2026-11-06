package com.kidstory.backend.dto;

import com.kidstory.backend.entity.StoryEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Masal sihirbazı. Serbest metin yoktur; ebeveyn yalnızca hazır seçenekleri işaretler. */
public record CreateStoryRequest(
        @NotNull(message = "Çocuk seçin") Long childId,
        @NotNull(message = "Ana karakter seçin") Long characterId,
        @Size(max = 2, message = "En fazla 2 yan karakter seçebilirsiniz") List<Long> sideCharacterIds,
        @NotNull(message = "Mekân seçin") Long locationId,
        @NotNull(message = "Öğretilecek değeri seçin") StoryEnums.Theme theme,
        StoryEnums.Situation situation,
        StoryEnums.Emotion emotion,
        StoryEnums.NarrationStyle narrationStyle,
        @Min(value = 4, message = "En az 4 sahne") @Max(value = 6, message = "En fazla 6 sahne") Integer sceneCount) {

    public static CreateStoryRequest empty(Long childId) {
        return new CreateStoryRequest(childId, null, List.of(), null, null, null, null,
                StoryEnums.NarrationStyle.WARM, 4);
    }
}
