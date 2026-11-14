package com.kidstory.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Yönetim paneli: masallar, ebeveynler, yapay zekâ sağlayıcıları ve yöneticiler. Her değişiklik denetim kaydı bırakır. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class AdminPanelTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void superAdminSeesEveryPage() throws Exception {
        MockHttpSession s = adminSession("testadmin", "TestAdmin123!");
        for (String path : new String[] {"/admin", "/admin/stories", "/admin/parents", "/admin/characters", "/admin/locations", "/admin/ai", "/admin/admins"}) {
            mvc.perform(get(path).session(s)).andExpect(status().isOk());
        }
    }

    @Test
    void superAdminCreatesAdminWhoCannotManageAdminsOrProviders() throws Exception {
        MockHttpSession superAdmin = adminSession("testadmin", "TestAdmin123!");
        String username = "editor" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/admin/admins").session(superAdmin).with(csrf())
                        .param("username", username).param("email", username + "@example.com")
                        .param("fullName", "Deneme Editör").param("password", "Editor123!").param("role", "ROLE_ADMIN"))
                .andExpect(status().is3xxRedirection());
        assertThat(audits("ADMIN_CREATED")).isPositive();

        MockHttpSession editor = adminSession(username, "Editor123!");
        mvc.perform(get("/admin").session(editor)).andExpect(status().isOk());
        mvc.perform(get("/admin/parents").session(editor)).andExpect(status().isOk());
        mvc.perform(get("/admin/characters").session(editor)).andExpect(status().isOk());
        mvc.perform(get("/admin/locations").session(editor)).andExpect(status().isOk());
        mvc.perform(get("/admin/admins").session(editor)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/ai").session(editor)).andExpect(status().isForbidden());
    }

    @Test
    void shortPasswordIsRejectedWhenCreatingAdmin() throws Exception {
        MockHttpSession superAdmin = adminSession("testadmin", "TestAdmin123!");
        mvc.perform(post("/admin/admins").session(superAdmin).with(csrf())
                        .param("username", "kisa").param("email", "kisa@example.com")
                        .param("fullName", "Kısa Parola").param("password", "123").param("role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Parola en az 10 karakter")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admins WHERE username = 'kisa'", Integer.class)).isZero();
    }

    @Test
    void adminGrantsCreditsAndSuspendsParent() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        registerParent(email);
        long parentId = jdbc.queryForObject("SELECT id FROM parents WHERE email = ?", Long.class, email);
        MockHttpSession s = adminSession("testadmin", "TestAdmin123!");

        mvc.perform(post("/admin/parents/" + parentId + "/credits").session(s).with(csrf()).param("amount", "5"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT story_credit_balance FROM credit_wallets WHERE parent_id = ?", Integer.class, parentId))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM credit_transactions t JOIN credit_wallets w ON w.id = t.wallet_id
                WHERE w.parent_id = ? AND t.transaction_type = 'ADMIN_ADJUSTMENT'""", Integer.class, parentId)).isEqualTo(1);

        mvc.perform(post("/admin/parents/" + parentId + "/active").session(s).with(csrf()).param("active", "false"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "Parola123!"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotEndWith("/app"));
        assertThat(audits("PARENT_CREDITS")).isPositive();
        assertThat(audits("PARENT_SUSPENDED")).isPositive();
    }

    @Test
    void superAdminChangesProvider() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM ai_providers_config WHERE provider_name = 'groq'", Long.class);
        MockHttpSession s = adminSession("testadmin", "TestAdmin123!");
        mvc.perform(post("/admin/ai/" + id).session(s).with(csrf()).param("active", "true").param("priority", "2"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT is_active FROM ai_providers_config WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT priority FROM ai_providers_config WHERE id = ?", Integer.class, id)).isEqualTo(2);
        jdbc.update("UPDATE ai_providers_config SET is_active = false, priority = 1 WHERE id = ?", id); // diğer testler etkilenmesin
    }

    @Test
    void adminApprovesPendingStory() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        registerParent(email);
        long parentId = jdbc.queryForObject("SELECT id FROM parents WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO child_profiles (parent_id, name, age) VALUES (?, 'Defne', 5)", parentId);
        long childId = jdbc.queryForObject("SELECT id FROM child_profiles WHERE parent_id = ?", Long.class, parentId);
        jdbc.update("""
                INSERT INTO stories (child_id, parent_id, title, theme, status, moderation_status)
                VALUES (?, ?, 'Onay Bekleyen Masal', 'SHARING', 'COMPLETED', 'PENDING')""", childId, parentId);
        long storyId = jdbc.queryForObject("SELECT id FROM stories WHERE parent_id = ?", Long.class, parentId);

        MockHttpSession s = adminSession("testadmin", "TestAdmin123!");
        mvc.perform(get("/admin/stories/" + storyId).session(s))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Onay Bekleyen Masal")))
                .andExpect(content().string(containsString("Onay bekliyor")))   // durum kodu değil, Türkçe etiketi
                .andExpect(content().string(containsString("Paylaşma")));
        mvc.perform(post("/admin/stories/" + storyId + "/moderate").session(s).with(csrf())
                        .param("decision", "APPROVED").param("reason", "Uygun"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT moderation_status FROM stories WHERE id = ?", String.class, storyId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_moderation_reviews WHERE story_id = ? AND decision = 'APPROVED'",
                Integer.class, storyId)).isEqualTo(1);
    }

    private MockHttpSession adminSession(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/admin/login").session(session).with(csrf()).param("username", username).param("password", password))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/admin"));
        return session;
    }

    private void registerParent(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"Test","lastName":"Ebeveyn","consent":true}""".formatted(email)))
                .andExpect(status().isCreated());
    }

    private int audits(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs WHERE action = ?", Integer.class, action);
    }
}
