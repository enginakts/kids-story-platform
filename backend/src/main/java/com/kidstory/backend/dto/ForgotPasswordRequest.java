package com.kidstory.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin") String email) {
}
