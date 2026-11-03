package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adım 3: e-posta doğrulama, parola sıfırlama, yenileme token'ı ve giriş kilidi. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, RecordingEmailSender.Config.class})
@ActiveProfiles("test")
class AccountFlowTests {

    private static final String PASSWORD = "Parola123!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingEmailSender mail;

    @Test
    void registrationSendsVerificationLinkThatVerifiesOnce() throws Exception {
        String email = email();
        register(email);
        assertThat(verified(email)).isFalse();

        String token = mail.lastToken(email, "/verify-email");
        mvc.perform(get("/verify-email").param("token", token))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("doğrulandı")));
        assertThat(verified(email)).isTrue();

        mvc.perform(get("/verify-email").param("token", token))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bağlantı geçersiz")));
    }

    @Test
    void resendInvalidatesPreviousVerificationLink() throws Exception {
        String email = email();
        String access = JsonPath.read(register(email).andReturn().getResponse().getContentAsString(), "$.accessToken");
        String first = mail.lastToken(email, "/verify-email");

        mvc.perform(post("/api/v1/account/verify-email/resend").header("Authorization", "Bearer " + access))
                .andExpect(status().isAccepted());
        String second = mail.lastToken(email, "/verify-email");

        verifyApi(first).andExpect(status().isBadRequest());
        verifyApi(second).andExpect(status().isNoContent());
    }

    @Test
    void passwordResetChangesPasswordAndEndsMobileSessions() throws Exception {
        String email = email();
        String refresh = JsonPath.read(register(email).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email))).andExpect(status().isAccepted());
        String token = mail.lastToken(email, "/reset-password");

        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"YeniParola456!\"}".formatted(token)))
                .andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "YeniParola456!").andExpect(status().isOk());
        refresh(refresh).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"BaskaParola789!\"}".formatted(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordGivesSameAnswerForUnknownEmail() throws Exception {
        String unknown = email();
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(unknown))).andExpect(status().isAccepted());
        assertThat(mail.to(unknown)).isEmpty();
    }

    @Test
    void webPasswordResetFlow() throws Exception {
        String email = email();
        register(email);
        mvc.perform(post("/forgot-password").with(csrf()).param("email", email))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bağlantısı gönderildi")));
        String token = mail.lastToken(email, "/reset-password");

        mvc.perform(get("/reset-password").param("token", token))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Parolayı değiştir")));
        mvc.perform(post("/reset-password").with(csrf()).param("token", token).param("newPassword", "WebParola123!"))
                .andExpect(redirectedUrl("/login?reset"));
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "WebParola123!"))
                .andExpect(redirectedUrl("/app"));
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesAllSessions() throws Exception {
        String email = email();
        String first = JsonPath.read(register(email).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        String body = refresh(first).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(body, "$.refreshToken");
        assertThat(second).isNotEqualTo(first);
        assertThat((String) JsonPath.read(body, "$.accessToken")).isNotBlank();

        refresh(first).andExpect(status().isBadRequest());
        refresh(second).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash IS NOT NULL AND revoked_at IS NULL"
                + " AND parent_id = (SELECT id FROM parents WHERE email = ?)", Integer.class, email)).isZero();
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refresh = JsonPath.read(register(email()).andReturn().getResponse().getContentAsString(), "$.refreshToken");
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh))).andExpect(status().isNoContent());
        refresh(refresh).andExpect(status().isBadRequest());
    }

    @Test
    void tokensAreStoredOnlyAsHashes() throws Exception {
        String email = email();
        String refresh = JsonPath.read(register(email).andReturn().getResponse().getContentAsString(), "$.refreshToken");
        String verify = mail.lastToken(email, "/verify-email");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, refresh)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE token_hash = ?", Integer.class, verify)).isZero();
    }

    @Test
    void repeatedWrongPasswordsLockTheAccountForApiAndWeb() throws Exception {
        String email = email();
        register(email);
        for (int i = 0; i < 5; i++) {
            login(email, "yanlis-parola").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isTooManyRequests());
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void parentCanEditChildOnWeb() throws Exception {
        String email = email();
        register(email);
        var session = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", PASSWORD));
        mvc.perform(post("/app/children").session(session).with(csrf()).param("name", "Deniz").param("age", "4"))
                .andExpect(redirectedUrl("/app/children"));
        Long id = jdbc.queryForObject("SELECT c.id FROM child_profiles c JOIN parents p ON p.id = c.parent_id WHERE p.email = ?",
                Long.class, email);

        mvc.perform(get("/app/children/" + id + "/edit").session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Deniz")));
        mvc.perform(post("/app/children/" + id).session(session).with(csrf())
                        .param("name", "Deniz").param("age", "6").param("bedtimeHour", "21:15"))
                .andExpect(redirectedUrl("/app/children"));
        assertThat(jdbc.queryForObject("SELECT age FROM child_profiles WHERE id = ?", Integer.class, id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT bedtime_hour::text FROM child_profiles WHERE id = ?", String.class, id))
                .isEqualTo("21:15:00");
    }

    private Boolean verified(String email) {
        return jdbc.queryForObject("SELECT is_email_verified FROM parents WHERE email = ?", Boolean.class, email);
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email, PASSWORD))).andExpect(status().isCreated()).andExpect(jsonPath("$.refreshToken").exists());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(token)));
    }

    private ResultActions verifyApi(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }
}
