package com.kidstory.backend.dto;

import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.service.CharacterCatalog;

import java.util.List;

/**
 * visual_anchor_tokens ve üretim alanları ebeveyne gösterilmez. images: yöneticinin yüklediği örnek resimlerin
 * adresleri (/library/images/{id}); ilki kapak olarak kullanılır, boşsa arayüz hazır gravürü gösterir.
 */
public record CharacterResponse(Long id, String name, String species, String speciesCode, String colorCode,
                                String colorLabel, String colorHex, String outfitCode, String outfitLabel,
                                String accessoryCode, String accessoryLabel, String personalityTraits,
                                boolean systemPreset, List<String> images) {

    public static CharacterResponse from(CharacterBible c) {
        return from(c, List.of());
    }

    public static CharacterResponse from(CharacterBible c, List<String> images) {
        CharacterCatalog.Color color = c.getColorCode() == null ? null : CharacterCatalog.Color.valueOf(c.getColorCode());
        CharacterCatalog.Outfit outfit = c.getOutfitCode() == null ? null : CharacterCatalog.Outfit.valueOf(c.getOutfitCode());
        CharacterCatalog.Accessory accessory = c.getAccessoryCode() == null ? null
                : CharacterCatalog.Accessory.valueOf(c.getAccessoryCode());
        return new CharacterResponse(c.getId(), c.getName(), c.getSpecies(), c.getSpeciesCode(), c.getColorCode(),
                color == null ? null : color.label(), color == null ? "#E6E0FF" : color.hex(),
                c.getOutfitCode(), outfit == null ? c.getClothingDescription() : outfit.label(),
                c.getAccessoryCode(), accessory == null ? null : accessory.label(),
                c.getPersonalityTraits(), c.isSystemPreset(), images);
    }

    /** Kapak resmi: ilk örnek resim; yoksa null. */
    public String cover() {
        return images == null || images.isEmpty() ? null : images.getFirst();
    }
}
