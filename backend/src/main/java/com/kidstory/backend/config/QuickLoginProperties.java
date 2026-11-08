package com.kidstory.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Geliştirme ortamındaki hızlı giriş hesapları (application-dev.properties). Yalnızca "dev" profilinde okunur;
 * üretimde bu ayarlar yoktur ve giriş sayfalarında hızlı giriş görünmez.
 */
@ConfigurationProperties("app.dev.quick-login")
public record QuickLoginProperties(List<Account> parents, List<Account> admins) {

    public QuickLoginProperties {
        parents = parents == null ? List.of() : List.copyOf(parents);
        admins = admins == null ? List.of() : List.copyOf(admins);
    }

    /** login: e-posta (ebeveyn) veya kullanıcı adı (yönetici). role yalnızca yöneticiler içindir. */
    public record Account(String login, String password, String label, String firstName, String lastName, String role) {}
}
