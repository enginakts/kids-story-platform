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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gerçek PostgreSQL üzerinde Flyway, Hibernate doğrulaması, güvenlik ve veri izolasyonu. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class PlatformIntegrationTests {

    private static final String CHILD = "{\"name\":\"Minik\",\"age\":4}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsCreateAllTablesAndSeedData() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'""", Integer.class);
        assertThat(tables).isEqualTo(29);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Integer.class)).isEqualTo(3);
    }

    @Test
    void registrationOpensWalletAndConsent() throws Exception {
        String email = email();
        register(email).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("""
                SELECT w.story_credit_balance FROM credit_wallets w JOIN parents p ON p.id = w.parent_id
                WHERE p.email = ?""", Integer.class, email)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM parent_consents c JOIN parents p ON p.id = c.parent_id
                WHERE p.email = ?""", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void registrationWithoutConsentIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Parola123!","firstName":"A","lastName":"B","consent":false}
                        """.formatted(email())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.consent").exists());
    }

    @Test
    void duplicateEmailConflicts() throws Exception {
        String email = email();
        register(email).andExpect(status().isCreated());
        register(email).andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTokenAndWrongPasswordIsUnauthorized() throws Exception {
        String email = email();
        register(email);
        login(email, "Parola123!").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists());
        login(email, "yanlis-parola").andExpect(status().isUnauthorized());
    }

    @Test
    void apiRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/children")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenGivesAccessToOwnChildren() throws Exception {
        String token = token(email());
        createChild(token).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/children").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Minik"));
    }

    @Test
    void ageOutsideThreeToSevenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/children").header("Authorization", bearer(token(email())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Minik\",\"age\":8}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parentCannotTouchAnotherParentsChild() throws Exception {
        String owner = token(email());
        String other = token(email());
        String body = createChild(owner).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/children").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/v1/children/" + id).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(CHILD))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/children/" + id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void childCanBeDeactivated() throws Exception {
        String token = token(email());
        Number id = JsonPath.read(createChild(token).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(delete("/api/v1/children/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/children").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void publicPagesRender() throws Exception {
        for (String path : new String[] {"/", "/login", "/register", "/admin/login"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }
    }

    @Test
    void quickLoginIsNotShownOutsideDevelopment() throws Exception {
        // Hızlı giriş yalnızca "dev" profilinde vardır; test ve üretimde giriş sayfalarında parola sızmamalı
        for (String path : new String[] {"/login", "/admin/login"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Hızlı giriş"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Deneme123!"))));
        }
    }

    @Test
    void explorePageIsRemoved() throws Exception {
        // Keşfet sayfası kaldırıldı: artık herkese açık değil, girişe yönlendirir
        mvc.perform(get("/explore")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void privatePagesRedirectToLogin() throws Exception {
        mvc.perform(get("/app")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void webRegistrationRequiresCsrfAndSignsParentIn() throws Exception {
        String email = email();
        mvc.perform(post("/register").param("email", email)).andExpect(status().isForbidden());
        mvc.perform(post("/register").with(csrf()).param("email", email).param("password", "Parola123!")
                        .param("firstName", "Ayşe").param("lastName", "Kaya").param("consent", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void parentAndAdminLoginsAreSeparate() throws Exception {
        String email = email();
        register(email);
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "Parola123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/app"));
        mvc.perform(post("/admin/login").with(csrf()).param("username", email).param("password", "Parola123!"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).contains("error"));
        mvc.perform(post("/admin/login").with(csrf()).param("username", "testadmin").param("password", "TestAdmin123!"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/admin"));
    }

    @Test
    void unapprovedStoryCannotBePublished() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO stories (child_id, parent_id, title, theme, published_at)
                VALUES (1, 1, 'x', 'SHARING', now())"""))
                .hasMessageContaining("chk_stories_published_approved");
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions createChild(String token) throws Exception {
        return mvc.perform(post("/api/v1/children").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(CHILD));
    }

    private String token(String email) throws Exception {
        return JsonPath.read(register(email).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }
}
