package com.kidstory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yönetim panelinde mekân formu. Ad, ortam türü ve atmosfer Türkçedir (ebeveyn görür, masal metni prompt'una gider);
 * resim tarifi, ışık ve sabit öğeler İngilizcedir ve her sahne resminin isteğine eklenir.
 */
public record LocationForm(
        @NotBlank(message = "Mekân adı zorunludur") @Size(max = 60, message = "Ad en fazla 60 karakter olabilir") String name,
        @NotBlank(message = "Ortam türü zorunludur") @Size(max = 60, message = "Ortam türü en fazla 60 karakter olabilir") String environmentType,
        @NotBlank(message = "Atmosfer zorunludur") @Size(max = 300, message = "Atmosfer en fazla 300 karakter olabilir") String atmosphereDescription,
        @NotBlank(message = "Resim tarifi zorunludur") @Size(max = 400, message = "Resim tarifi en fazla 400 karakter olabilir") String visualAnchorTokens,
        @NotBlank(message = "Işık zorunludur") @Size(max = 100, message = "Işık en fazla 100 karakter olabilir") String lightingStyle,
        @Size(max = 300, message = "Sabit öğeler en fazla 300 karakter olabilir") String recurringLandmarks) {

    public static LocationForm empty() {
        return new LocationForm("", "", "", "", "", "");
    }
}
