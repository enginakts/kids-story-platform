package com.kidstory.backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParentRegisterRequest(
        @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin") String email,
        @NotBlank(message = "Parola zorunludur") @Size(min = 8, max = 72, message = "Parola 8-72 karakter olmalıdır") String password,
        @NotBlank(message = "Ad zorunludur") @Size(max = 100) String firstName,
        @NotBlank(message = "Soyad zorunludur") @Size(max = 100) String lastName,
        @AssertTrue(message = "Devam etmek için ebeveyn onayı gereklidir") boolean consent) {
}
