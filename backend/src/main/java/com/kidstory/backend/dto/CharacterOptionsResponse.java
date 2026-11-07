package com.kidstory.backend.dto;

import com.kidstory.backend.service.CharacterCatalog;

import java.util.List;

/** Mobil ve web formlarının seçenek listeleri. */
public record CharacterOptionsResponse(List<CharacterCatalog.Choice> species, List<CharacterCatalog.Choice> colors,
                                       List<CharacterCatalog.Choice> outfits, List<CharacterCatalog.Choice> accessories) {

    public static CharacterOptionsResponse create() {
        return new CharacterOptionsResponse(
                CharacterCatalog.choices(CharacterCatalog.Species.values()),
                CharacterCatalog.choices(CharacterCatalog.Color.values()),
                CharacterCatalog.choices(CharacterCatalog.Outfit.values()),
                CharacterCatalog.choices(CharacterCatalog.Accessory.values()));
    }
}
