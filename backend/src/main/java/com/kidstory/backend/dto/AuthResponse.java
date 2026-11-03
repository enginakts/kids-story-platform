package com.kidstory.backend.dto;

/** expiresIn erişim token'ının saniye cinsinden ömrüdür. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
}
