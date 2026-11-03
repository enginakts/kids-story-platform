package com.kidstory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank(message = "Parola zorunludur") @Size(min = 8, max = 72, message = "Parola 8-72 karakter olmalıdır") String newPassword) {
}
