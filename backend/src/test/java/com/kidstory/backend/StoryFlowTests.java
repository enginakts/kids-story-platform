package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import com.kidstory.backend.service.story.GenerationWorker;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Masal sihirbazı ve üretim hattı: metin → sahneler → görsel + ses, çocuğa göre kütüphane, kredi ve izolasyon. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class StoryFlowTests {

    private static final String PASSWORD = "Parola123!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GenerationWorker worker;

    @Test
    void storyIsWrittenSplitIntoScenesAndGetsImagesAndAudio() throws Exception {
        Parent p = verifiedParent();
        Long child = child(p.token, "Ela");

        Number storyId = JsonPath.read(createStory(p.token, child, 5).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(credits(p.email)).isEqualTo(2);

        worker.runPending();

        mvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", bearer(p.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.title").value(containsString("Pamuk")))
                .andExpect(jsonPath("$.characterName").value("Pamuk"))
                .andExpect(jsonPath("$.sideCharacterNames[0]").value("Leo"))
                .andExpect(jsonPath("$.emotionLabel").value("Empati"))
                .andExpect(jsonPath("$.scenes.length()").value(5))
                .andExpect(jsonPath("$.scenes[*].hasImage").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.scenes[*].hasAudio").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));

        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/image").header("Authorization", bearer(p.token)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/audio").header("Authorization", bearer(p.token)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).startsWith("audio/"));
    }

    @Test
    void everySceneUsesTheSameBibleDescriptionsAndSeed() throws Exception {
        Parent p = verifiedParent();
        Number storyId = JsonPath.read(createStory(p.token, child(p.token, "Can"), 4)
                .andReturn().getResponse().getContentAsString(), "$.id");
        worker.runPending();

        List<String> prompts = jdbc.queryForList(
                "SELECT image_prompt FROM story_scenes WHERE story_id = ? ORDER BY scene_number", String.class, storyId);
        assertThat(prompts).hasSize(4).allSatisfy(prompt -> assertThat(prompt)
                .contains("small fluffy white bunny", "pastel blue scarf", "cute lion cub", "sunny whimsical forest",
                        "soft watercolor"));
        assertThat(jdbc.queryForList("SELECT DISTINCT generation_seed FROM story_scenes WHERE story_id = ?",
                Long.class, storyId)).containsExactly(101001L);
    }

    @Test
    void storiesAreKeptSeparatelyForEachChild() throws Exception {
        Parent p = verifiedParent();
        Long ela = child(p.token, "Ela");
        Long can = child(p.token, "Can");
        createStory(p.token, ela, 4).andExpect(status().isAccepted());
        createStory(p.token, ela, 4).andExpect(status().isAccepted());
        createStory(p.token, can, 4).andExpect(status().isAccepted());

        mvc.perform(get("/api/v1/children/" + ela + "/stories").header("Authorization", bearer(p.token)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].childId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(ela.intValue()))));
        mvc.perform(get("/api/v1/children/" + can + "/stories").header("Authorization", bearer(p.token)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anotherParentCannotSeeStoryOrItsFiles() throws Exception {
        Parent owner = verifiedParent();
        Parent other = verifiedParent();
        Long child = child(owner.token, "Ela");
        Number storyId = JsonPath.read(createStory(owner.token, child, 4).andReturn().getResponse().getContentAsString(), "$.id");
        worker.runPending();

        mvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/image").header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/children/" + child + "/stories").header("Authorization", bearer(other.token)))
                .andExpect(status().isNotFound());
        createStory(other.token, child, 4).andExpect(status().isNotFound());
    }

    @Test
    void unverifiedEmailAndEmptyWalletCannotCreateStories() throws Exception {
        String email = email();
        String token = token(email);
        Long child = child(token, "Ela");
        createStory(token, child, 4).andExpect(status().isConflict());

        jdbc.update("UPDATE parents SET is_email_verified = TRUE WHERE email = ?", email);
        jdbc.update("UPDATE credit_wallets SET story_credit_balance = 0 WHERE parent_id = (SELECT id FROM parents WHERE email = ?)", email);
        createStory(token, child, 4).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Masal krediniz kalmadı"));
    }

    @Test
    void failedGenerationMarksStoryFailedAndRefundsCredit() throws Exception {
        Parent p = verifiedParent();
        Number storyId = JsonPath.read(createStory(p.token, child(p.token, "Ela"), 4)
                .andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.update("UPDATE generation_jobs SET max_attempts = 1 WHERE story_id = ?", storyId);
        jdbc.update("UPDATE ai_providers_config SET is_active = FALSE WHERE provider_type = 'LLM'");
        try {
            worker.runPending();
        } finally {
            jdbc.update("UPDATE ai_providers_config SET is_active = TRUE WHERE provider_type = 'LLM'");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, storyId)).isEqualTo("FAILED");
        assertThat(credits(p.email)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_transactions WHERE story_id = ? AND transaction_type = 'REFUND'",
                Integer.class, storyId)).isEqualTo(1);
    }

    @Test
    void parentUsesWizardOnWebAndSeesStoryInChildLibrary() throws Exception {
        Parent p = verifiedParent();
        Long child = child(p.token, "Deniz");
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", p.email).param("password", PASSWORD));

        mvc.perform(get("/app/stories/new").param("childId", child.toString()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Pamuk")))
                .andExpect(content().string(containsString("Uyku vakti")));
        String location = mvc.perform(post("/app/stories").session(session).with(csrf())
                        .param("childId", child.toString()).param("characterId", characterId("Fikri").toString())
                        .param("locationId", locationId("Yıldızlı Oda").toString()).param("theme", "SELF_CARE")
                        .param("situation", "BEDTIME").param("emotion", "").param("narrationStyle", "BEDTIME")
                        .param("sceneCount", "4"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        worker.runPending();

        mvc.perform(get(location).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Fikri")));
        mvc.perform(get("/app/children/" + child).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hazır")));
        mvc.perform(get("/app").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Deniz")));
    }

    @Test
    void wizardRequiresMainCharacterAndTheme() throws Exception {
        Parent p = verifiedParent();
        mvc.perform(post("/api/v1/stories").header("Authorization", bearer(p.token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"childId\":%d}".formatted(child(p.token, "Ela"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.characterId").exists())
                .andExpect(jsonPath("$.errors.theme").exists());
    }

    private record Parent(String email, String token) {
    }

    private Parent verifiedParent() throws Exception {
        String email = email();
        String token = token(email);
        jdbc.update("UPDATE parents SET is_email_verified = TRUE WHERE email = ?", email);
        return new Parent(email, token);
    }

    private ResultActions createStory(String token, Long childId, int scenes) throws Exception {
        return mvc.perform(post("/api/v1/stories").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"childId":%d,"characterId":%d,"sideCharacterIds":[%d],"locationId":%d,"theme":"SHARING",
                         "situation":"SHARING_TOYS","emotion":"EMPATHY","narrationStyle":"WARM","sceneCount":%d}
                        """.formatted(childId, characterId("Pamuk"), characterId("Leo"), locationId("Çiçekli Orman"), scenes)));
    }

    private Long child(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/children").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\",\"age\":5}".formatted(name)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long characterId(String name) {
        return jdbc.queryForObject("SELECT id FROM character_bibles WHERE name = ? AND is_system_preset", Long.class, name);
    }

    private Long locationId(String name) {
        return jdbc.queryForObject("SELECT id FROM location_bibles WHERE name = ? AND is_system_preset", Long.class, name);
    }

    private Integer credits(String email) {
        return jdbc.queryForObject("""
                SELECT w.story_credit_balance FROM credit_wallets w JOIN parents p ON p.id = w.parent_id
                WHERE p.email = ?""", Integer.class, email);
    }

    private String token(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email, PASSWORD))).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }
}
