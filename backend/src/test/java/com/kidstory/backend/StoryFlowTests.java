package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import com.kidstory.backend.service.StoryGenerationWorker;
import com.kidstory.backend.service.ai.LlmRequest;
import com.kidstory.backend.service.ai.RateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adım 5: Trendyol (metin) + Gemini (sahneler), kredi, arka plan üretimi, güvenlik, kişiselleştirme ve iade. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, ScriptedLlmClient.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.generation.worker-enabled=false")
class StoryFlowTests {

    private static final String TEXT = """
            Kiko'nun Paylaşma Günü

            Kiko'ya bir sepet çilek geldi.

            Kiko'nun gülümsemesi parladı.

            Dostlar el ele tutuştu.

            Herkes huzurla uyudu.""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("textLlm") ScriptedLlmClient text;
    @Autowired @Qualifier("sceneLlm") ScriptedLlmClient scenes;
    @Autowired StoryGenerationWorker worker;

    private String token;
    private String email;
    private long childId;
    private long presetId;
    private long locationId;

    @BeforeEach
    void setUp() throws Exception {
        text.reset();
        scenes.reset();
        // Taslak sağlayıcılar devre dışı: gerçek sağlayıcı hatası sessizce taslağa düşmesin, yeniden deneme test edilsin.
        jdbc.update("UPDATE ai_providers_config SET is_active = FALSE WHERE provider_name LIKE 'stub%'");
        jdbc.update("UPDATE generation_jobs SET status = 'SUCCEEDED' WHERE status IN ('QUEUED', 'RUNNING')");
        email = UUID.randomUUID() + "@example.com";
        token = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email))).andReturn().getResponse().getContentAsString(), "$.accessToken");
        jdbc.update("UPDATE parents SET is_email_verified = TRUE WHERE email = ?", email);
        childId = ((Number) JsonPath.read(mvc.perform(post("/api/v1/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\",\"age\":5,\"favoriteAnimals\":\"Kedi\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        presetId = jdbc.queryForObject("SELECT min(id) FROM character_bibles WHERE is_system_preset", Long.class);
        locationId = jdbc.queryForObject("SELECT min(id) FROM location_bibles", Long.class);
    }

    @Test
    void trendyolWritesGeminiSplitsAndStoryCompletes() throws Exception {
        long storyId = id(request(true).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING")));
        assertThat(credits()).isEqualTo(2);

        text.willReturn(TEXT);
        scenes.willReturn(story("Kiko'nun Paylaşma Günü", "Kiko'ya bir sepet çilek geldi.", "Kiko'nun gülümsemesi parladı."));
        assertThat(worker.runOnce()).isTrue();

        mvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.story.status").value("COMPLETED"))
                .andExpect(jsonPath("$.story.title").value("Ada'nın Paylaşma Günü"))
                .andExpect(jsonPath("$.scenes.length()").value(4))
                .andExpect(jsonPath("$.scenes[0].text").value("Ada'ya bir sepet çilek geldi."))
                .andExpect(jsonPath("$.scenes[0].imagePrompt").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT image_prompt FROM story_scenes WHERE story_id = ? AND scene_number = 1",
                String.class, storyId)).contains("a small child").contains("Style: soft watercolor").doesNotContain("Ada").doesNotContain("Kiko");
        assertThat(jdbc.queryForObject("SELECT provider_name FROM generation_jobs WHERE story_id = ?", String.class, storyId))
                .isEqualTo("featherless+gemini");
    }

    @Test
    void geminiReceivesTrendyolTextAndNeitherReceivesTheChildName() throws Exception {
        request(true);
        text.willReturn(TEXT);
        scenes.willReturn(story("Başlık", "Kiko ve Pamuk oynadı.", "Herkes mutlu oldu."));
        worker.runOnce();

        LlmRequest toTrendyol = text.requests.getFirst();
        assertThat(toTrendyol.systemPrompt() + toTrendyol.userPrompt()).doesNotContain("Ada").contains("Kiko")
                .contains("Yaş: 5").contains("Paylaşma").contains("Kedi").endsWith("/no_think");
        assertThat(toTrendyol.schema()).isNull();

        LlmRequest toGemini = scenes.requests.getFirst();
        assertThat(toGemini.systemPrompt() + toGemini.userPrompt()).doesNotContain("Ada")
                .contains("Kiko'ya bir sepet çilek geldi.").contains("Başlık: Kiko'nun Paylaşma Günü")
                .contains("tam olarak 5 sahneye").contains("small fluffy white bunny").doesNotContain("Kedi");
        assertThat(toGemini.schema()).containsKey("properties");
    }

    @Test
    void unsafeTextIsRejectedBeforeReachingGemini() throws Exception {
        long storyId = id(request(false));
        text.willReturn("Başlık\n\nOrmanda korkunç bir canavar vardı ve herkes çok sessizce bekledi.");
        worker.runOnce();
        assertThat(jobStatus(storyId)).isEqualTo("QUEUED");
        assertThat(scenes.requests).isEmpty();
        assertThat(jdbc.queryForObject("SELECT last_error FROM generation_jobs WHERE story_id = ?", String.class, storyId))
                .contains("Kara liste");
    }

    @Test
    void failuresInEitherStageRetryThenRefund() throws Exception {
        long storyId = id(request(false));
        text.willFail(new IllegalStateException("Featherless isteği başarısız: HTTP 503"));
        worker.runOnce();

        retryNow(storyId);
        text.willReturn(TEXT.replace("Kiko'nun ", "").replace("Kiko'ya", "Pamuk'a").replace("Kiko'nun", "Pamuk'un"));
        scenes.willReturn("bozuk json");
        worker.runOnce();

        retryNow(storyId);
        text.willReturn(TEXT.replace("Kiko'nun ", "").replace("Kiko'ya", "Pamuk'a").replace("Kiko'nun", "Pamuk'un"));
        scenes.willFail(new IllegalStateException("Gemini isteği başarısız: HTTP 500"));
        worker.runOnce();

        assertThat(jobStatus(storyId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, storyId)).isEqualTo("FAILED");
        assertThat(credits()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_transactions WHERE story_id = ? AND transaction_type = 'REFUND'",
                Integer.class, storyId)).isEqualTo(1);
    }

    @Test
    void rateLimitDelaysRetryByRetryAfter() throws Exception {
        long storyId = id(request(false));
        text.willFail(new RateLimitedException("Featherless", 120));
        worker.runOnce();
        assertThat(jobStatus(storyId)).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("SELECT run_after > now() + interval '100 seconds' FROM generation_jobs WHERE story_id = ?",
                Boolean.class, storyId)).isTrue();
        assertThat(worker.runOnce()).isFalse();
    }

    @Test
    void childPlaceholderWhenNotAllowedIsRejected() throws Exception {
        long storyId = id(request(false));
        text.willReturn(TEXT);
        worker.runOnce();
        assertThat(jobStatus(storyId)).isEqualTo("QUEUED");
        assertThat(scenes.requests).isEmpty();
    }

    @Test
    void unverifiedEmailCannotRequestStory() throws Exception {
        jdbc.update("UPDATE parents SET is_email_verified = FALSE WHERE email = ?", email);
        request(true).andExpect(status().isForbidden());
        assertThat(credits()).isEqualTo(3);
    }

    @Test
    void noCreditsMeansConflictAndNoStory() throws Exception {
        jdbc.update("UPDATE credit_wallets SET story_credit_balance = 0 WHERE parent_id = (SELECT id FROM parents WHERE email = ?)", email);
        request(true).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stories s JOIN parents p ON p.id = s.parent_id WHERE p.email = ?",
                Integer.class, email)).isZero();
    }

    @Test
    void cannotUseAnotherParentsChildOrSeeTheirStory() throws Exception {
        long storyId = id(request(true));
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"B","lastName":"C","consent":true}
                """.formatted(UUID.randomUUID() + "@example.com"))).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stories").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.length()").value(0));

        Long ownCharacter = ((Number) JsonPath.read(mvc.perform(post("/api/v1/characters").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bulut\",\"speciesCode\":\"KEDI\",\"colorCode\":\"MAVI\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        jdbc.update("UPDATE parents SET is_email_verified = TRUE WHERE is_email_verified = FALSE");
        mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                        .content(body(childId, presetId, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.childId").exists());
        mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body(childId, ownCharacter, true))).andExpect(status().isAccepted());
    }

    @Test
    void webWizardCreatesStoryAndShowsProgress() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));
        mvc.perform(get("/app/stories/new").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Masal kimin için?")))
                .andExpect(content().string(containsString("Çiçekli Orman")));
        String location = mvc.perform(post("/app/stories").session(session).with(csrf())
                        .param("childId", String.valueOf(childId)).param("primaryCharacterId", String.valueOf(presetId))
                        .param("locationId", String.valueOf(locationId)).param("theme", "COURAGE")
                        .param("narrationStyle", "BEDTIME").param("length", "SHORT").param("includeChild", "true"))
                .andExpect(redirectedUrlPattern("/app/stories/*")).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(location).session(session)).andExpect(content().string(containsString("Masalınız hazırlanıyor")));

        text.willReturn("Cesur Kiko\n\nKiko yıldızlara el salladı.\n\nAy ona gülümsedi.\n\nDostlar geldi.\n\nHerkes uyudu.");
        scenes.willReturn(story("Cesur Kiko", "Kiko yıldızlara el salladı.", "Ay ona gülümsedi."));
        worker.runOnce();
        mvc.perform(get(location).session(session)).andExpect(content().string(containsString("Cesur Ada")));
        mvc.perform(get("/app").session(session)).andExpect(content().string(containsString("Cesur Ada")));
    }

    private void retryNow(long storyId) {
        jdbc.update("UPDATE generation_jobs SET run_after = now() WHERE story_id = ?", storyId);
    }

    private ResultActions request(boolean includeChild) throws Exception {
        return mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(childId, presetId, includeChild)));
    }

    private String body(long child, long character, boolean includeChild) {
        return """
                {"childId":%d,"primaryCharacterId":%d,"sideCharacterIds":[],"locationId":%d,"theme":"SHARING",
                 "narrationStyle":"WARM","length":"MEDIUM","includeChild":%s}""".formatted(child, character, locationId, includeChild);
    }

    private long id(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private int credits() {
        return jdbc.queryForObject("SELECT story_credit_balance FROM credit_wallets w JOIN parents p ON p.id = w.parent_id WHERE p.email = ?",
                Integer.class, email);
    }

    private String jobStatus(long storyId) {
        return jdbc.queryForObject("SELECT status FROM generation_jobs WHERE story_id = ?", String.class, storyId);
    }

    private static String story(String title, String first, String second) {
        return """
                {"title":"%s","scenes":[
                {"text":"%s","image_prompt":"Kiko waves at the stars"},
                {"text":"%s","image_prompt":"a bunny smiling"},
                {"text":"Dostlar el ele tutuştu.","image_prompt":"friends holding hands"},
                {"text":"Herkes huzurla uyudu.","image_prompt":"friends sleeping under the moon"}]}""".formatted(title, first, second);
    }
}
