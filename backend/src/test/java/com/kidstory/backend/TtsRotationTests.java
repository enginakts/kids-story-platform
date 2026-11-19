package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import com.kidstory.backend.service.StoryGenerationWorker;
import com.kidstory.backend.service.ai.RateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Seslendirme sağlayıcısı masal başına seçilir (TtsPlanner, V21): masallar sağlayıcılara sırayla dağıtılır, bir masalın
 * bütün sahneleri aynı sağlayıcıdan gelir, kotası dolmuş ya da yeni kota hatası vermiş sağlayıcı atlanır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, ScriptedLlmClient.Config.class, TtsRotationTests.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.generation.worker-enabled=false")
class TtsRotationTests {

    private static final String TEXT = """
            Kiko'nun Paylaşma Günü

            Kiko'ya bir sepet çilek geldi.

            Kiko'nun gülümsemesi parladı.

            Dostlar el ele tutuştu.

            Herkes huzurla uyudu.""";

    private static final String SCENES = StoryFlowTests.padScenes("""
            {"title":"Kiko'nun Paylaşma Günü","scenes":[
            {"text":"Kiko'ya bir sepet çilek geldi.","image_prompt":"a basket of strawberries"},
            {"text":"Kiko'nun gülümsemesi parladı.","image_prompt":"a smiling child"},
            {"text":"Dostlar el ele tutuştu.","image_prompt":"friends holding hands"},
            {"text":"Herkes huzurla uyudu.","image_prompt":"friends sleeping under the moon"}]}""");

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        ScriptedTtsClient geminiTts() {
            return new ScriptedTtsClient("gemini-tts");
        }

        @Bean
        ScriptedTtsClient murfTts() {
            return new ScriptedTtsClient("murf");
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("textLlm") ScriptedLlmClient text;
    @Autowired @Qualifier("sceneLlm") ScriptedLlmClient scenes;
    @Autowired @Qualifier("geminiTts") ScriptedTtsClient gemini;
    @Autowired @Qualifier("murfTts") ScriptedTtsClient murf;
    @Autowired StoryGenerationWorker worker;

    private String token;
    private long childId;

    @BeforeEach
    void setUp() throws Exception {
        text.reset();
        scenes.reset();
        gemini.reset();
        murf.reset();
        jdbc.update("UPDATE ai_providers_config SET is_active = FALSE WHERE provider_name LIKE 'stub%'");
        jdbc.update("UPDATE ai_providers_config SET is_active = TRUE, daily_limit = NULL, monthly_limit = NULL WHERE provider_type = 'TTS' AND provider_name IN ('gemini-tts', 'murf')");
        jdbc.update("UPDATE generation_jobs SET status = 'SUCCEEDED' WHERE status IN ('QUEUED', 'RUNNING')");
        // Başka test sınıflarının kayıtları sırayı etkilemesin
        jdbc.update("DELETE FROM ai_usage_events WHERE provider_type = 'TTS'");
        jdbc.update("UPDATE stories SET tts_provider = NULL");
        String email = UUID.randomUUID() + "@example.com";
        token = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email))).andReturn().getResponse().getContentAsString(), "$.accessToken");
        jdbc.update("UPDATE parents SET is_email_verified = TRUE WHERE email = ?", email);
        childId = ((Number) JsonPath.read(mvc.perform(post("/api/v1/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\",\"age\":5}"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void storiesAreSpreadAcrossProvidersAndEachStoryKeepsOneVoice() throws Exception {
        long first = completedStory();
        runAll();
        long second = completedStory();
        runAll();
        long third = completedStory();
        runAll();

        String p1 = provider(first), p2 = provider(second), p3 = provider(third);
        assertThat(p1).isNotEqualTo(p2);
        assertThat(p3).isEqualTo(p1);
        // Her masalın 4 sahnesi de kendi sağlayıcısından
        for (long id : new long[]{first, second, third}) {
            assertThat(jdbc.queryForList("SELECT DISTINCT provider_name FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO' AND status = 'SUCCEEDED'",
                    String.class, id)).containsExactly(provider(id));
        }
        assertThat(gemini.requests.size() + murf.requests.size()).isEqualTo(12);
        assertThat(gemini.requests).isNotEmpty();
        assertThat(murf.requests).isNotEmpty();
    }

    @Test
    void providerThatJustHitItsQuotaIsSkipped() throws Exception {
        // Sıra murf'teydi (gemini en son kullanılan) ama murf az önce "kota bitti" dedi
        long earlier = completedStory();
        jdbc.update("UPDATE stories SET tts_provider = 'gemini-tts' WHERE id = ?", earlier);
        jdbc.update("""
                INSERT INTO ai_usage_events (provider_type, provider_name, model_name, is_success, http_status, duration_ms)
                VALUES ('TTS', 'murf', 'en-US-natalie', FALSE, 402, 100)""");
        runAll();
        long next = completedStory();
        runAll();
        assertThat(provider(next)).isEqualTo("gemini-tts");
        assertThat(murf.requests).isEmpty();
    }

    @Test
    void dailyLimitReachedMovesStoriesToTheOtherProvider() throws Exception {
        jdbc.update("UPDATE ai_providers_config SET daily_limit = 2 WHERE provider_type = 'TTS' AND provider_name = 'gemini-tts'");
        jdbc.update("""
                INSERT INTO ai_usage_events (provider_type, provider_name, model_name, is_success, http_status, duration_ms)
                VALUES ('TTS', 'gemini-tts', 'm', TRUE, 200, 100), ('TTS', 'gemini-tts', 'm', TRUE, 200, 100)""");
        long a = completedStory();
        runAll();
        long b = completedStory();
        runAll();
        assertThat(provider(a)).isEqualTo("murf");
        assertThat(provider(b)).isEqualTo("murf");
        assertThat(gemini.requests).isEmpty();
    }

    @Test
    void busyStoryProviderIsRetriedWithTheSameVoiceAndOnlyTheLastAttemptFallsBack() throws Exception {
        murf.failWith(new RateLimitedException("Murf", 30));
        // gemini en son kullanılmış gibi: sıra murf'te
        long earlier = completedStory();
        jdbc.update("UPDATE stories SET tts_provider = 'gemini-tts' WHERE id = ?", earlier);
        jdbc.update("UPDATE generation_jobs SET status = 'SUCCEEDED' WHERE story_id = ?", earlier);
        long storyId = completedStory();

        // 1. ve 2. deneme: yalnızca masalın sağlayıcısı (murf) denenir, sahneler sessiz kalmaz ama ses de karışmaz
        runAll();
        assertThat(provider(storyId)).isEqualTo("murf");
        assertThat(gemini.requests).isEmpty();
        jdbc.update("UPDATE generation_jobs SET run_after = now() WHERE story_id = ?", storyId);
        runAll();
        assertThat(gemini.requests).isEmpty();
        assertThat(murf.requests).hasSize(8);

        // 3. (son) deneme: diğer sağlayıcıya geçilir
        jdbc.update("UPDATE generation_jobs SET run_after = now() WHERE story_id = ?", storyId);
        runAll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_scenes WHERE story_id = ? AND audio_url IS NOT NULL", Integer.class, storyId))
                .isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT DISTINCT provider_name FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO' AND status = 'SUCCEEDED'",
                String.class, storyId)).containsExactly("gemini-tts");
    }

    @Test
    void storyProviderOutOfQuotaMidStoryFallsBackRightAway() throws Exception {
        long earlier = completedStory();
        jdbc.update("UPDATE stories SET tts_provider = 'gemini-tts' WHERE id = ?", earlier);
        jdbc.update("UPDATE generation_jobs SET status = 'SUCCEEDED' WHERE story_id = ?", earlier);
        long storyId = completedStory();
        // Masal murf'e atanmış ve bir sahnesi seslendirilmişken murf'ün kotası biter
        jdbc.update("UPDATE stories SET tts_provider = 'murf' WHERE id = ?", storyId);
        jdbc.update("UPDATE story_scenes SET audio_url = 'stories/x/scene-1.mp3' WHERE story_id = ? AND scene_number = 1", storyId);
        jdbc.update("""
                INSERT INTO ai_usage_events (provider_type, provider_name, model_name, is_success, http_status, duration_ms)
                VALUES ('TTS', 'murf', 'en-US-natalie', FALSE, 402, 100)""");
        murf.failWith(new IllegalStateException("Murf karakter kotası bitti (HTTP 402)"));
        runAll();
        assertThat(provider(storyId)).isEqualTo("murf");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_scenes WHERE story_id = ? AND audio_url IS NOT NULL", Integer.class, storyId))
                .isEqualTo(4);
        assertThat(gemini.requests).isNotEmpty();
    }

    private String provider(long storyId) {
        return jdbc.queryForObject("SELECT tts_provider FROM stories WHERE id = ?", String.class, storyId);
    }

    private long completedStory() throws Exception {
        long presetId = jdbc.queryForObject("SELECT min(id) FROM character_bibles WHERE is_system_preset", Long.class);
        long locationId = jdbc.queryForObject("SELECT min(id) FROM location_bibles", Long.class);
        long storyId = ((Number) JsonPath.read(mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"childId":%d,"primaryCharacterId":%d,"sideCharacterIds":[],"locationId":%d,"theme":"SHARING",
                         "narrationStyle":"WARM","length":"SHORT","includeChild":true}""".formatted(childId, presetId, locationId)))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        text.willReturn(TEXT);
        scenes.willReturn(SCENES);
        assertThat(worker.runOnce()).isTrue();
        return storyId;
    }

    private void runAll() {
        while (worker.runOnce()) {
            // kuyruk boşalana kadar
        }
    }
}
