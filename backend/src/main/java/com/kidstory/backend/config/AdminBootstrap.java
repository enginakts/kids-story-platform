package com.kidstory.backend.config;

import com.kidstory.backend.entity.Admin;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.repository.AdminRepository;
import com.kidstory.backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** İlk yönetici, parola kod veya migration içinde olmadan ortam ayarlarından oluşturulur. */
@Component
@RequiredArgsConstructor
class AdminBootstrap implements ApplicationRunner {

    private final AdminRepository admins;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:}")
    private String username;

    @Value("${app.bootstrap.admin.email:}")
    private String email;

    @Value("${app.bootstrap.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank() || admins.count() > 0) {
            return;
        }
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setFullName("Sistem Yöneticisi");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(roles.findByName(Role.SUPER_ADMIN).orElseThrow());
        admins.save(admin);
    }
}
