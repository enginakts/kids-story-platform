package com.kidstory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Görünüm alanları serbest metin değil, CharacterCatalog kodlarıdır. */
public record CharacterRequest(
        @NotBlank(message = "Karakter adı zorunludur") @Size(max = 40, message = "Ad en fazla 40 karakter olabilir") String name,
        @NotBlank(message = "Lütfen bir tür seçin") String speciesCode,
        @NotBlank(message = "Lütfen bir renk seçin") String colorCode,
        String outfitCode,
        String accessoryCode,
        @Size(max = 300, message = "Kişilik en fazla 300 karakter olabilir") String personalityTraits) {
}
