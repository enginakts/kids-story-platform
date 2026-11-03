package com.kidstory.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ParentLoginRequest(@NotBlank String email, @NotBlank String password) {
}
