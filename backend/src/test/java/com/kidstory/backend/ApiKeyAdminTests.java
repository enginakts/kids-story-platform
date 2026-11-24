package com.kidstory.backend;

import com.kidstory.backend.service.ApiKeyRegistry;
import com.kidstory.backend.service.ApiKeyStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Süper yönetici API anahtarı yönetimi (V22): .env'den birincil ve yedek anahtar, açma/kapatma, otomatik kapanma. */
@SpringBootTest(properties = {"app.ai.groq.api-key=gsk_test-birincil-1111", "GROQ_API_KEY_2=gsk_test-yedek-2222"})
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class ApiKeyAdminTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApiKeyStateService states;
    @Autowired ApiKeyRegistry registry;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM api_key_states");
    }

    @Test
    void registryReadsPrimaryAndNumberedBackupKeys() {
        assertThat(registry.ring("groq").credentials()).extracting(c -> c.envName())
                .containsExactly("GROQ_API_KEY", "GROQ_API_KEY_2");
        assertThat(registry.ring("murf").credentials()).isEmpty();
    }

    @Test
    void pageShowsMaskedKeysAndAdminCanCloseAndReopen() throws Exception {
        MockHttpSession s = adminSession("testadmin", "TestAdmin123!");
        mvc.perform(get("/admin/keys").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("GROQ_API_KEY_2")))
                .andExpect(content().string(containsString("••••1111")))
                .andExpect(content().string(not(containsString("gsk_test-birincil"))))
                .andExpect(content().string(containsString("2 / 2 anahtar açık")));

        mvc.perform(post("/admin/keys/groq/1").session(s).with(csrf()).param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/keys#groq")).andExpect(flash().attributeExists("notice"));
        assertThat(states.usable("groq", 1)).isFalse();
        assertThat(registry.ring("groq").<Integer>call(c -> c.slot())).isEqualTo(2);
        mvc.perform(get("/admin/keys").session(s)).andExpect(content().string(containsString("Kapalı (yönetici)")))
                .andExpect(content().string(containsString("1 / 2 anahtar açık")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs WHERE action = 'API_KEY_DISABLED'", Long.class)).isPositive();

        mvc.perform(post("/admin/keys/groq/1").session(s).with(csrf()).param("enabled", "true"));
        assertThat(states.usable("groq", 1)).isTrue();
        mvc.perform(post("/admin/keys/groq/7").session(s).with(csrf()).param("enabled", "false"))
                .andExpect(flash().attributeExists("problem"));
    }

    @Test
    void autoDisabledKeyReopensWhenQuotaResetsButInvalidKeyDoesNot() {
        states.autoDisable("gemini-tts", 1, 429, "Gemini TTS hız sınırına ulaşıldı");
        assertThat(states.usable("gemini-tts", 1)).isFalse();
        // Servis başına ayrı: aynı anahtar sahne bölmede açık kalır
        assertThat(states.usable("gemini", 1)).isTrue();
        assertThat(jdbc.queryForObject("SELECT reenable_at > now() FROM api_key_states WHERE service = 'gemini-tts' AND slot = 1", Boolean.class)).isTrue();
        jdbc.update("UPDATE api_key_states SET reenable_at = now() - interval '1 minute' WHERE service = 'gemini-tts'");
        assertThat(states.usable("gemini-tts", 1)).isTrue();
        assertThat(jdbc.queryForObject("SELECT enabled FROM api_key_states WHERE service = 'gemini-tts' AND slot = 1", Boolean.class)).isTrue();

        states.autoDisable("murf", 2, 401, "Murf anahtarı reddedildi: HTTP 401");
        assertThat(jdbc.queryForObject("SELECT reenable_at IS NULL FROM api_key_states WHERE service = 'murf'", Boolean.class)).isTrue();
        assertThat(states.usable("murf", 2)).isFalse();
    }

    @Test
    void usageIsCountedPerKeyPerDay() {
        states.used("groq", 2);
        states.used("groq", 2);
        assertThat(states.state(states.all(), "groq", 2).callsToday()).isEqualTo(2);
        jdbc.update("UPDATE api_key_states SET calls_day = calls_day - 1");
        assertThat(states.state(states.all(), "groq", 2).callsToday()).isZero();
        states.used("groq", 2);
        assertThat(states.state(states.all(), "groq", 2).callsToday()).isEqualTo(1);
    }

    @Test
    void onlySuperAdminManagesKeys() throws Exception {
        MockHttpSession superAdmin = adminSession("testadmin", "TestAdmin123!");
        String username = "editor" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/admin/admins").session(superAdmin).with(csrf())
                .param("username", username).param("email", username + "@example.com")
                .param("fullName", "Deneme Editör").param("password", "Editor123!").param("role", "ROLE_ADMIN"));
        MockHttpSession editor = adminSession(username, "Editor123!");
        mvc.perform(get("/admin/keys").session(editor)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/keys/groq/1").session(editor).with(csrf()).param("enabled", "false")).andExpect(status().isForbidden());
    }

    private MockHttpSession adminSession(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/admin/login").session(session).with(csrf()).param("username", username).param("password", password))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/admin"));
        return session;
    }
}
