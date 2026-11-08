package com.kidstory.backend.dto;

import com.kidstory.backend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Yönetim panelinde yeni yönetici formu. */
public record AdminForm(
        @NotBlank(message = "Kullanıcı adı zorunludur")
        @Size(min = 3, max = 100, message = "Kullanıcı adı 3-100 karakter olmalıdır")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Yalnızca harf, rakam, nokta, tire ve alt çizgi kullanın") String username,
        @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin") @Size(max = 255) String email,
        @NotBlank(message = "Ad soyad zorunludur") @Size(max = 150) String fullName,
        @NotBlank(message = "Parola zorunludur")
        @Size(min = 10, max = 72, message = "Parola en az 10 karakter olmalıdır") String password,
        @NotBlank(message = "Rol seçin") String role) {

    public static AdminForm empty() {
        return new AdminForm("", "", "", "", Role.ADMIN);
    }
}
