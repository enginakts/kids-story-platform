package com.kidstory.backend.dto;

import com.kidstory.backend.entity.StoryEnums;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Masal sihirbazı. Serbest metin alanı yoktur; tüm seçimler listelerden gelir. */
public record StoryRequest(
        @NotNull(message = "Lütfen bir çocuk seçin") Long childId,
        @NotNull(message = "Lütfen bir ana karakter seçin") Long primaryCharacterId,
        @Size(max = 2, message = "En fazla 2 yan karakter seçebilirsiniz") List<Long> sideCharacterIds,
        @NotNull(message = "Lütfen bir mekân seçin") Long locationId,
        @NotNull(message = "Lütfen bir tema seçin") StoryEnums.Theme theme,
        @NotNull(message = "Lütfen bir anlatım biçimi seçin") StoryEnums.NarrationStyle narrationStyle,
        @NotNull(message = "Lütfen masal uzunluğunu seçin") StoryEnums.Length length,
        boolean includeChild) {
}
