package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import com.kidstory.backend.service.StoryGenerationWorker;
import com.kidstory.backend.service.ai.TtsClient;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adım 7: sahne sesleri kuyruğu, dosya sunumu ve sahiplik, hata ve yeniden isteme, okuyucu sayfası. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, ScriptedLlmClient.Config.class, ScriptedTtsClient.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.generation.worker-enabled=false")
class StoryAudioFlowTests {

    private static final String TEXT = """
            Kiko'nun Paylaşma Günü

            Kiko'ya bir sepet çilek geldi.

            Kiko'nun gülümsemesi parladı.

            Dostlar el ele tutuştu.

            Herkes huzurla uyudu.""";

    private static final String SCENES = """
            {"title":"Kiko'nun Paylaşma Günü","scenes":[
            {"text":"Kiko'ya bir sepet çilek geldi.","image_prompt":"a basket of strawberries"},
            {"text":"Kiko'nun gülümsemesi parladı.","image_prompt":"a smiling child"},
            {"text":"Dostlar el ele tutuştu.","image_prompt":"friends holding hands"},
            {"text":"Herkes huzurla uyudu.","image_prompt":"friends sleeping under the moon"}]}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("textLlm") ScriptedLlmClient text;
    @Autowired @Qualifier("sceneLlm") ScriptedLlmClient scenes;
    @Autowired ScriptedTtsClient tts;
    @Autowired StoryGenerationWorker worker;

    private String token;
    private String email;
    private long childId;

    @BeforeEach
    void setUp() throws Exception {
        text.reset();
        scenes.reset();
        tts.reset();
        jdbc.update("UPDATE ai_providers_config SET is_active = FALSE WHERE provider_name LIKE 'stub%'");
        jdbc.update("UPDATE generation_jobs SET status = 'SUCCEEDED' WHERE status IN ('QUEUED', 'RUNNING')");
        email = UUID.randomUUID() + "@example.com";
        token = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email))).andReturn().getResponse().getContentAsString(), "$.accessToken");
        jdbc.update("UPDATE parents SET is_email_verified = TRUE WHERE email = ?", email);
        childId = ((Number) JsonPath.read(mvc.perform(post("/api/v1/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\",\"age\":5}"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void completedStoryQueuesSceneAudioThatIsSynthesizedAndServed() throws Exception {
        long storyId = completedStory("BEDTIME");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO' AND status = 'QUEUED'",
                Integer.class, storyId)).isEqualTo(4);
        api(storyId).andExpect(jsonPath("$.story.status").value("COMPLETED"))
                .andExpect(jsonPath("$.audio.status").value("PREPARING"))
                .andExpect(jsonPath("$.scenes[0].audioUrl").doesNotExist());

        runAll();

        mvc.perform(get("/api/v1/stories").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].audioReady").value(true));
        api(storyId).andExpect(jsonPath("$.audio.status").value("READY"))
                .andExpect(jsonPath("$.audio.ready").value(4))
                .andExpect(jsonPath("$.scenes[0].audioUrl").value("/api/v1/stories/" + storyId + "/scenes/1/audio"))
                .andExpect(jsonPath("$.scenes[0].audioDurationSeconds").value(3.25));
        // Ses kişiselleştirilmiş metinden üretilir; yatmadan önce masalı daha yavaş okunur.
        TtsClient.SpeechRequest first = tts.requests.getFirst();
        assertThat(first.text()).isEqualTo("Ada'ya bir sepet çilek geldi.");
        assertThat(first.speed()).isEqualTo(0.88);
        assertThat(first.voiceId()).isEqualTo("nisan");

        byte[] audio = mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/audio").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(audio, StandardCharsets.UTF_8)).isEqualTo("ID3-ses:Ada'ya bir sepet çilek geldi.");
        // Tarayıcı oynatıcısı parça parça ister (Range)
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/audio").header("Authorization", "Bearer " + token).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent());
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/9/audio").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherParentCannotDownloadTheAudio() throws Exception {
        long storyId = completedStory("WARM");
        runAll();
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"B","lastName":"C","consent":true}
                """.formatted(UUID.randomUUID() + "@example.com"))).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/audio").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/stories/" + storyId + "/audio").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/audio")).andExpect(status().isUnauthorized());
    }

    @Test
    void audioFailureKeepsStoryAndCreditAndCanBeRequestedAgain() throws Exception {
        long storyId = completedStory("WARM");
        int creditsAfterStory = credits();
        tts.failWith(new IllegalStateException("Kikiri ses servisine ulaşılamadı (http://localhost:8000)"));
        for (int attempt = 0; attempt < 3; attempt++) {
            jdbc.update("UPDATE generation_jobs SET run_after = now() WHERE story_id = ?", storyId);
            runAll();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO' AND status = 'FAILED'",
                Integer.class, storyId)).isEqualTo(4);
        api(storyId).andExpect(jsonPath("$.story.status").value("COMPLETED"))
                .andExpect(jsonPath("$.audio.status").value("FAILED"));
        assertThat(credits()).isEqualTo(creditsAfterStory);

        tts.reset();
        mvc.perform(post("/api/v1/stories/" + storyId + "/audio").header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PREPARING"));
        // Bekleyen iş varken ikinci istek yeni iş açmaz
        mvc.perform(post("/api/v1/stories/" + storyId + "/audio").header("Authorization", "Bearer " + token));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO' AND status = 'QUEUED'",
                Integer.class, storyId)).isEqualTo(4);
        runAll();
        api(storyId).andExpect(jsonPath("$.audio.status").value("READY"));
    }

    @Test
    void newStoryTextIsNotDelayedByQueuedAudio() throws Exception {
        long first = completedStory("WARM");
        long second = id(requestStory("WARM"));
        text.willReturn(TEXT);
        scenes.willReturn(SCENES);
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, second)).isEqualTo("COMPLETED");
        assertThat(tts.requests).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO' AND status = 'QUEUED'",
                Integer.class, first)).isEqualTo(4);
    }

    @Test
    void webReaderShowsPlayersAndStatusAndServesAudioToOwner() throws Exception {
        long storyId = completedStory("WARM");
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));
        mvc.perform(get("/app/stories/" + storyId).session(session))
                .andExpect(content().string(containsString("Masal seslendiriliyor")))
                .andExpect(content().string(containsString("0 / 4 sahne hazır")))
                .andExpect(content().string(containsString("data-reader")));
        mvc.perform(get("/app/stories/" + storyId + "/media-status").session(session))
                .andExpect(jsonPath("$.audio.status").value("PREPARING")).andExpect(jsonPath("$.scenes[0].audio").doesNotExist());

        runAll();
        mvc.perform(get("/app/stories/" + storyId).session(session))
                .andExpect(content().string(containsString("Sesli masal hazır")))
                .andExpect(content().string(containsString("/app/stories/" + storyId + "/scenes/4/audio")))
                .andExpect(content().string(containsString("Kikiri Türkçe TTS")))
                .andExpect(content().string(not(containsString("Sesi hazırla"))));
        mvc.perform(get("/app").session(session)).andExpect(content().string(containsString("Sesli")));
        mvc.perform(get("/app/stories/" + storyId + "/media-status").session(session))
                .andExpect(jsonPath("$.audio.status").value("READY")).andExpect(jsonPath("$.scenes[1].audio").value("/app/stories/" + storyId + "/scenes/2/audio"));
        mvc.perform(get("/app/stories/" + storyId + "/scenes/2/audio").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "audio/mpeg"));
        mvc.perform(get("/app/stories/" + storyId + "/scenes/2/audio")).andExpect(status().is3xxRedirection());
    }

    @Test
    void webCanRequestAudioForStoryWithoutAudio() throws Exception {
        long storyId = completedStory("WARM");
        jdbc.update("DELETE FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO'", storyId);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));
        mvc.perform(get("/app/stories/" + storyId).session(session)).andExpect(content().string(containsString("Sesi hazırla")));
        mvc.perform(post("/app/stories/" + storyId + "/audio").session(session).with(csrf()))
                .andExpect(redirectedUrl("/app/stories/" + storyId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_AUDIO'",
                Integer.class, storyId)).isEqualTo(4);
    }

    private long completedStory(String style) throws Exception {
        long storyId = id(requestStory(style));
        text.willReturn(TEXT);
        scenes.willReturn(SCENES);
        assertThat(worker.runOnce()).isTrue();
        return storyId;
    }

    private org.springframework.test.web.servlet.ResultActions requestStory(String style) throws Exception {
        long presetId = jdbc.queryForObject("SELECT min(id) FROM character_bibles WHERE is_system_preset", Long.class);
        long locationId = jdbc.queryForObject("SELECT min(id) FROM location_bibles", Long.class);
        return mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"childId":%d,"primaryCharacterId":%d,"sideCharacterIds":[],"locationId":%d,"theme":"SHARING",
                         "narrationStyle":"%s","length":"SHORT","includeChild":true}""".formatted(childId, presetId, locationId, style)));
    }

    private org.springframework.test.web.servlet.ResultActions api(long storyId) throws Exception {
        return mvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + token));
    }

    private void runAll() {
        while (worker.runOnce()) {
            // kuyruk boşalana kadar
        }
    }

    private long id(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private int credits() {
        return jdbc.queryForObject("SELECT story_credit_balance FROM credit_wallets w JOIN parents p ON p.id = w.parent_id WHERE p.email = ?",
                Integer.class, email);
    }
}
