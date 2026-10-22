package com.kidstory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginRequest {
    @NotBlank(message = "Kullanıcı adı veya e-posta zorunludur")
    private String usernameOrEmail;

    @NotBlank(message = "Şifre alanı zorunludur")
    private String password;
}
