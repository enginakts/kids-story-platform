package com.kidstory.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private String userType; // PARENT veya ADMIN
    private Long id;
    private String email;
    private String username;
    private String fullName;
    private String role;
    private Integer storyCredits;
    private Integer interactiveCredits;
}
