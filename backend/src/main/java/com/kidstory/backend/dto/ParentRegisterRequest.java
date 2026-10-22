package com.kidstory.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParentRegisterRequest {
    @NotBlank(message = "Ad alanı zorunludur")
    private String firstName;

    @NotBlank(message = "Soyad alanı zorunludur")
    private String lastName;

    @NotBlank(message = "E-posta alanı zorunludur")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    private String email;

    @NotBlank(message = "Şifre alanı zorunludur")
    @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
    private String password;

    private String phoneNumber;
    private String preferredLanguage;
}
