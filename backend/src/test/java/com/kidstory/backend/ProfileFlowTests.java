package com.kidstory.backend;

import com.kidstory.backend.service.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ebeveyn profili: hesap bilgileri, parola, KVKK veri indirme ve zincirleme hesap silme. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, RecordingEmailSender.Config.class})
@ActiveProfiles("test")
class ProfileFlowTests {

    private static final String PASSWORD = "Parola123!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MediaStorage storage;

    private String email;
    private long parentId;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email, PASSWORD))).andExpect(status().isCreated());
        parentId = jdbc.queryForObject("SELECT id FROM parents WHERE email = ?", Long.class, email);
        session = login(email, PASSWORD);
    }

    @Test
    void profileShowsSummaryAndUpdatesName() throws Exception {
        mvc.perform(post("/app/children").session(session).with(csrf()).param("name", "Deniz").param("age", "4"))
                .andExpect(redirectedUrl("/app/children"));
        mvc.perform(get("/app/profile").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Profilim")))
                .andExpect(content().string(containsString("Tehlikeli bölge")))
                .andExpect(content().string(containsString("kayıtlı çocuk")))
                .andExpect(content().string(containsString("role=\"dialog\"")))
                .andExpect(content().string(containsString("aria-modal=\"true\"")));

        mvc.perform(post("/app/profile/name").session(session).with(csrf()).param("firstName", "  Zeynep ").param("lastName", "Demir"))
                .andExpect(redirectedUrl("/app/profile#hesap")).andExpect(flash().attributeExists("notice"));
        assertThat(jdbc.queryForObject("SELECT first_name || ' ' || last_name FROM parents WHERE id = ?", String.class, parentId))
                .isEqualTo("Zeynep Demir");
        // Menüdeki ad yeniden giriş gerekmeden güncellenir
        mvc.perform(get("/app/profile").session(session)).andExpect(content().string(containsString(">Zeynep<")));

        mvc.perform(post("/app/profile/name").session(session).with(csrf()).param("firstName", " ").param("lastName", "Demir"))
                .andExpect(flash().attributeExists("problem"));
        assertThat(jdbc.queryForObject("SELECT first_name FROM parents WHERE id = ?", String.class, parentId)).isEqualTo("Zeynep");
    }

    @Test
    void passwordChangeRequiresCurrentPassword() throws Exception {
        mvc.perform(post("/app/profile/password").session(session).with(csrf())
                        .param("currentPassword", "yanlis-parola").param("newPassword", "YeniParola456!"))
                .andExpect(flash().attribute("problem", "Mevcut parola yanlış"));
        mvc.perform(post("/app/profile/password").session(session).with(csrf())
                        .param("currentPassword", PASSWORD).param("newPassword", "kisa"))
                .andExpect(flash().attributeExists("problem"));
        mvc.perform(post("/app/profile/password").session(session).with(csrf())
                        .param("currentPassword", PASSWORD).param("newPassword", "YeniParola456!"))
                .andExpect(flash().attributeExists("notice"));

        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", PASSWORD))
                .andExpect(redirectedUrlPattern("/login?error*"));
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "YeniParola456!"))
                .andExpect(redirectedUrl("/app/children"));
    }

    @Test
    void exportContainsNamesAndTextsButNoMedia() throws Exception {
        long storyId = storyWithScene();
        storage.store("stories/" + storyId + "/scene-1-test.png", new byte[] {1, 2, 3});
        jdbc.update("UPDATE story_scenes SET image_url = ? WHERE story_id = ?", "stories/" + storyId + "/scene-1-test.png", storyId);

        String json = mvc.perform(get("/app/profile/export").session(session)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"takmaAd\":\"Deniz\"").contains("Deniz'in Çilek Günü").contains("Deniz sepete baktı.")
                .contains(email).doesNotContain("stories/").doesNotContain(".png").doesNotContain("password");
    }

    @Test
    void kvkkPageIsPublicAndLinkedFromProfile() throws Exception {
        mvc.perform(get("/kvkk")).andExpect(status().isOk()).andExpect(content().string(containsString("KVKK aydınlatma metni")));
        mvc.perform(get("/app/profile").session(session)).andExpect(content().string(containsString("href=\"/kvkk\"")));
    }

    @Test
    void deleteNeedsWordAndPasswordAndChangesNothingOtherwise() throws Exception {
        long storyId = storyWithScene();
        mvc.perform(post("/app/profile/delete").session(session).with(csrf()).param("confirmWord", "evet").param("password", PASSWORD))
                .andExpect(redirectedUrl("/app/profile#sil")).andExpect(flash().attributeExists("deleteProblem"));
        mvc.perform(post("/app/profile/delete").session(session).with(csrf()).param("confirmWord", "SİL").param("password", "yanlis"))
                .andExpect(redirectedUrl("/app/profile#sil")).andExpect(flash().attribute("deleteProblem", "Parola yanlış"));
        assertThat(count("SELECT count(*) FROM parents WHERE id = ?", parentId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM stories WHERE id = ?", storyId)).isEqualTo(1);
        mvc.perform(get("/app/profile").session(session)).andExpect(status().isOk()); // oturum açık kalır
    }

    @Test
    void deleteRemovesEverythingAndLogsOut() throws Exception {
        long storyId = storyWithScene();
        String key = "stories/" + storyId + "/scene-1-test.png";
        storage.store(key, new byte[] {1, 2, 3});
        long childId = jdbc.queryForObject("SELECT child_id FROM stories WHERE id = ?", Long.class, storyId);
        jdbc.update("INSERT INTO generation_jobs (story_id, job_type, status) VALUES (?, 'SCENE_IMAGE', 'SUCCEEDED')", storyId);
        jdbc.update("""
                INSERT INTO ai_usage_events (provider_type, provider_name, model_name, story_id, is_success, duration_ms)
                VALUES ('IMAGE', 'cloudflare', 'profil-testi', ?, TRUE, 100)""", storyId);
        Long adminId = jdbc.queryForObject("SELECT min(id) FROM admins", Long.class);
        jdbc.update("INSERT INTO admin_audit_logs (admin_id, action, target_entity, target_id, details) VALUES (?, 'PARENT_SUSPENDED', 'parent', ?, ?)",
                adminId, parentId, email);

        mvc.perform(post("/app/profile/delete").session(session).with(csrf()).param("confirmWord", "sil").param("password", PASSWORD))
                .andExpect(redirectedUrl("/?hesapSilindi"));

        assertThat(count("SELECT count(*) FROM parents WHERE id = ?", parentId)).isZero();
        assertThat(count("SELECT count(*) FROM child_profiles WHERE id = ?", childId)).isZero();
        assertThat(count("SELECT count(*) FROM stories WHERE parent_id = ?", parentId)).isZero();
        assertThat(count("SELECT count(*) FROM story_scenes WHERE story_id = ?", storyId)).isZero();
        assertThat(count("SELECT count(*) FROM generation_jobs WHERE story_id = ?", storyId)).isZero();
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE parent_id = ?", parentId)).isZero();
        assertThat(count("SELECT count(*) FROM credit_wallets WHERE parent_id = ?", parentId)).isZero();
        assertThat(count("SELECT count(*) FROM parent_consents WHERE parent_id = ?", parentId)).isZero();
        assertThat(storage.load(key)).isEmpty(); // dosya da silindi
        // Kullanım kaydı anonim kalır (karar); denetim kaydındaki e-posta anonimleşir
        assertThat(jdbc.queryForObject("SELECT story_id FROM ai_usage_events WHERE model_name = 'profil-testi'", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT details FROM admin_audit_logs WHERE target_entity = 'parent' AND target_id = ?", String.class, parentId))
                .isEqualTo("[silinmiş hesap]");
        jdbc.update("DELETE FROM ai_usage_events WHERE model_name = 'profil-testi'");

        // Oturum kapandı; ana sayfada mesaj görünür; aynı bilgilerle giriş yapılamaz
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/").param("hesapSilindi", "")).andExpect(content().string(containsString("Hesabınız ve bütün verileriniz silindi")));
        mvc.perform(get("/")).andExpect(content().string(not(containsString("Hesabınız ve bütün verileriniz silindi"))));
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", PASSWORD))
                .andExpect(redirectedUrlPattern("/login?error*"));
    }

    /** Yayımlanmış masal (Keşfet) silinmez: sahipsiz kalır, çocuğun adı nötr adla değişir, sesi silinir, resmi kalır (V15). */
    @Test
    void deleteKeepsPublishedStoriesAnonymized() throws Exception {
        long privateStory = storyWithScene();
        long published = storyWithScene();
        jdbc.update("""
                UPDATE stories SET title = 'Deniz''in Cesur Günü', personalized_used = TRUE, visibility = 'PUBLIC',
                    moderation_status = 'APPROVED', saved_at = now(), published_at = now() WHERE id = ?""", published);
        jdbc.update("UPDATE story_scenes SET scene_text = ?, voice_text = ?, image_url = ?, audio_url = ? WHERE story_id = ?",
                "Deniz'e bir kuş el salladı. Deniz gülümsedi.", "Deniz'e bir kuş el salladı. Deniz gülümsedi.",
                "stories/" + published + "/scene-1-art.png", "stories/" + published + "/scene-1-voice.mp3", published);
        jdbc.update("INSERT INTO generation_jobs (story_id, job_type, status) VALUES (?, 'SCENE_AUDIO', 'QUEUED')", published);
        storage.store("stories/" + published + "/scene-1-art.png", new byte[] {1});
        storage.store("stories/" + published + "/scene-1-voice.mp3", new byte[] {2});
        storage.store("stories/" + privateStory + "/scene-1-test.png", new byte[] {3});

        mvc.perform(post("/app/profile/delete").session(session).with(csrf()).param("confirmWord", "SİL").param("password", PASSWORD))
                .andExpect(redirectedUrl("/?hesapSilindi"));

        assertThat(count("SELECT count(*) FROM stories WHERE id = ?", privateStory)).isZero();
        assertThat(storage.load("stories/" + privateStory + "/scene-1-test.png")).isEmpty();

        var kept = jdbc.queryForMap("SELECT parent_id, child_id, title FROM stories WHERE id = ?", published);
        assertThat(kept.get("parent_id")).isNull();
        assertThat(kept.get("child_id")).isNull();
        assertThat(kept.get("title")).isEqualTo("Minik'in Cesur Günü");
        var scene = jdbc.queryForMap("SELECT scene_text, voice_text, image_url, audio_url FROM story_scenes WHERE story_id = ?", published);
        assertThat(scene.get("scene_text")).isEqualTo("Minik'e bir kuş el salladı. Minik gülümsedi.");
        assertThat(scene.get("voice_text")).isEqualTo("Minik'e bir kuş el salladı. Minik gülümsedi.");
        assertThat(scene.get("audio_url")).isNull();
        assertThat(scene.get("image_url")).isEqualTo("stories/" + published + "/scene-1-art.png");
        assertThat(storage.load("stories/" + published + "/scene-1-art.png")).isPresent();   // resim kalır
        assertThat(storage.load("stories/" + published + "/scene-1-voice.mp3")).isEmpty();   // ses silinir
        assertThat(count("SELECT count(*) FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO'", published)).isZero();
        jdbc.update("DELETE FROM stories WHERE id = ?", published); // test verisini temizle
        storage.deleteFolder("stories/" + published);
    }

    private long storyWithScene() throws Exception {
        mvc.perform(post("/app/children").session(session).with(csrf()).param("name", "Deniz").param("age", "4"));
        long childId = jdbc.queryForObject("SELECT max(id) FROM child_profiles WHERE parent_id = ?", Long.class, parentId);
        long storyId = jdbc.queryForObject("""
                INSERT INTO stories (child_id, parent_id, title, theme, status) VALUES (?, ?, 'Deniz''in Çilek Günü', 'SHARING', 'COMPLETED')
                RETURNING id""", Long.class, childId, parentId);
        jdbc.update("INSERT INTO story_scenes (story_id, scene_number, scene_text, voice_text, image_prompt) VALUES (?, 1, ?, ?, 'a basket')",
                storyId, "Deniz sepete baktı.", "Deniz sepete baktı.");
        return storyId;
    }

    private MockHttpSession login(String user, String password) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/login").session(s).with(csrf()).param("username", user).param("password", password))
                .andExpect(redirectedUrl("/app/children"));
        return s;
    }

    private long count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Long.class, arg);
    }
}
