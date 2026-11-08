package com.kidstory.backend.config;

import com.kidstory.backend.dto.CreateChildRequest;
import com.kidstory.backend.dto.ParentRegisterRequest;
import com.kidstory.backend.entity.Admin;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.repository.AdminRepository;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.RoleRepository;
import com.kidstory.backend.service.AuthService;
import com.kidstory.backend.service.ChildProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Locale;

/**
 * Yalnızca geliştirme ortamı: hızlı giriş hesapları yoksa oluşturur, böylece .devdb silinse de düğmeler çalışır.
 * Var olan hesaplara dokunmaz. Ebeveynlerin e-postası doğrulanmış sayılır ve birer çocuk profili eklenir.
 * İlk süper yöneticiyi AdminBootstrap oluşturduğu için ondan sonra çalışır.
 */
@Slf4j
@Profile("dev")
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties(QuickLoginProperties.class)
@RequiredArgsConstructor
class DevAccountsSeeder implements ApplicationRunner {

    private final QuickLoginProperties accounts;
    private final ParentRepository parents;
    private final AdminRepository admins;
    private final RoleRepository roles;
    private final AuthService auth;
    private final ChildProfileService children;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (QuickLoginProperties.Account a : accounts.parents()) {
            String email = a.login().trim().toLowerCase(Locale.ROOT);
            if (parents.existsByEmail(email)) {
                continue;
            }
            Parent parent = auth.register(new ParentRegisterRequest(email, a.password(), a.firstName(), a.lastName(), true), "127.0.0.1");
            parent.setEmailVerified(true);
            children.create(parent.getId(), new CreateChildRequest("Defne", 5, null, "Tavşan", "Turuncu", null, LocalTime.of(20, 30)));
            log.info("Geliştirme hesabı oluşturuldu: {}", email);
        }
        for (QuickLoginProperties.Account a : accounts.admins()) {
            if (admins.existsByUsernameIgnoreCase(a.login())) {
                continue;
            }
            Admin admin = new Admin();
            admin.setUsername(a.login());
            admin.setEmail(a.login().toLowerCase(Locale.ROOT) + "@example.com");
            admin.setFullName((a.firstName() + " " + a.lastName()).trim());
            admin.setPasswordHash(passwordEncoder.encode(a.password()));
            admin.setRole(roles.findByName(a.role() == null ? Role.ADMIN : a.role()).orElseThrow());
            admins.save(admin);
            log.info("Geliştirme yöneticisi oluşturuldu: {}", a.login());
        }
    }
}
