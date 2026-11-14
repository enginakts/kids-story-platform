package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import com.kidstory.backend.service.StoryGenerationWorker;
import com.kidstory.backend.service.ai.ImageClient;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adım 6: sahne resimleri, karakter referans portreleri, kapak, sahiplik, hata ve yeniden isteme. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, ScriptedLlmClient.Config.class, ScriptedImageClient.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.generation.worker-enabled=false")
class StoryImageFlowTests {

    private static final String TEXT = """
            Kiko'nun Paylaşma Günü

            Kiko'ya bir sepet çilek geldi.

            Kiko'nun gülümsemesi parladı.

            Dostlar el ele tutuştu.

            Herkes huzurla uyudu.""";

    private static final String SCENES = """
            {"title":"Kiko'nun Paylaşma Günü","scenes":[
            {"text":"Kiko'ya bir sepet çilek geldi.","image_prompt":"Kiko holds a basket of strawberries"},
            {"text":"Kiko'nun gülümsemesi parladı.","image_prompt":"Kiko smiles brightly"},
            {"text":"Dostlar el ele tutuştu.","image_prompt":"friends holding hands"},
            {"text":"Herkes huzurla uyudu.","image_prompt":"friends sleeping under the moon"}]}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("textLlm") ScriptedLlmClient text;
    @Autowired @Qualifier("sceneLlm") ScriptedLlmClient scenes;
    @Autowired ScriptedImageClient images;
    @Autowired StoryGenerationWorker worker;

    private String token;
    private String email;
    private long childId;
    private long characterId;

    @BeforeEach
    void setUp() throws Exception {
        text.reset();
        scenes.reset();
        images.reset();
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
        // Her test kendi (yöneticinin eklediği) karakteriyle başlar: referans portresi henüz yok.
        characterId = jdbc.queryForObject("""
                INSERT INTO character_bibles (name, species, species_code, color_code, outfit_code, accessory_code,
                                              visual_anchor_tokens, is_system_preset)
                VALUES (?, 'Kedi', 'KEDI', 'MAVI', 'YOK', 'YOK', 'cute small sky blue kitten', TRUE) RETURNING id""",
                Long.class, "Bulut " + UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    void scenesGetImagesWithOneCharacterReferenceReusedAcrossScenes() throws Exception {
        long storyId = completedStory();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generation_jobs WHERE story_id = ? AND job_type = 'SCENE_IMAGE' AND status = 'QUEUED'",
                Integer.class, storyId)).isEqualTo(4);
        api(storyId).andExpect(jsonPath("$.images.status").value("PREPARING"));

        runAll();

        // 1 karakter portresi + 4 sahne
        assertThat(images.requests).hasSize(5);
        ImageClient.ImageRequest portrait = images.requests.getFirst();
        assertThat(portrait.prompt()).contains("Character reference portrait").contains("plain white background");
        assertThat(portrait.references()).isEmpty();
        List<ImageClient.ImageRequest> sceneRequests = images.requests.subList(1, 5);
        for (ImageClient.ImageRequest r : sceneRequests) {
            assertThat(r.references()).hasSize(1);
            BufferedImage ref = ImageIO.read(new ByteArrayInputStream(r.references().getFirst()));
            assertThat(Math.max(ref.getWidth(), ref.getHeight())).isLessThan(512);
            assertThat(r.width()).isEqualTo(1024);
            assertThat(r.height()).isEqualTo(768);
            // Çocuğun adı ve yer tutucu görsel prompt'una gitmez
            assertThat(r.prompt()).contains("reference images").contains("Style:").doesNotContain("Ada").doesNotContain("Kiko");
            // Mekânın çevre bilgisi (yönetim panelinden) her sahne resmine gider
            assertThat(r.prompt()).contains("Setting: sunny whimsical forest").contains("lighting: soft warm morning sunlight")
                    .contains("recurring landmarks: a big friendly oak tree");
        }
        assertThat(sceneRequests.getFirst().prompt()).contains("a small child holds a basket of strawberries");
        assertThat(jdbc.queryForObject("SELECT reference_image_url FROM character_bibles WHERE id = ?", String.class, characterId))
                .startsWith("characters/" + characterId + "/reference-");

        api(storyId).andExpect(jsonPath("$.images.status").value("READY"))
                .andExpect(jsonPath("$.scenes[0].imageUrl").value("/api/v1/stories/" + storyId + "/scenes/1/image"));
        mvc.perform(get("/api/v1/stories").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].coverReady").value(true));
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/2/image").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));

        // İkinci masal aynı karakterin portresini yeniden üretmez
        images.reset();
        completedStory();
        runAll();
        assertThat(images.requests).hasSize(4);
        assertThat(images.requests).allSatisfy(r -> assertThat(r.references()).hasSize(1));
    }

    @Test
    void anotherParentCannotSeeTheImages() throws Exception {
        long storyId = completedStory();
        runAll();
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"B","lastName":"C","consent":true}
                """.formatted(UUID.randomUUID() + "@example.com"))).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/stories/" + storyId + "/scenes/1/image").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/stories/" + storyId + "/images").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void imageFailureKeepsStoryAndPortraitIsKeptForTheRetry() throws Exception {
        long storyId = completedStory();
        int credits = credits();
        // Portre üretilir, sahne resmi başarısız olur: portre kaybolmamalı
        images.failWith(new IllegalStateException("Cloudflare görsel isteği başarısız: HTTP 500"), 1);
        for (int attempt = 0; attempt < 3; attempt++) {
            jdbc.update("UPDATE generation_jobs SET run_after = now() WHERE story_id = ?", storyId);
            runAll();
        }
        api(storyId).andExpect(jsonPath("$.story.status").value("COMPLETED"))
                .andExpect(jsonPath("$.images.status").value("FAILED"));
        assertThat(credits()).isEqualTo(credits);
        assertThat(jdbc.queryForObject("SELECT reference_image_url FROM character_bibles WHERE id = ?", String.class, characterId))
                .isNotNull();

        images.reset();
        mvc.perform(post("/api/v1/stories/" + storyId + "/images").header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PREPARING"));
        runAll();
        api(storyId).andExpect(jsonPath("$.images.status").value("READY"));
        // Saklanan portre yeniden kullanılır: yalnızca 4 sahne isteği
        assertThat(images.requests).hasSize(4);
    }

    @Test
    void webShowsImagesCoverAndProgress() throws Exception {
        long storyId = completedStory();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));
        mvc.perform(get("/app/stories/" + storyId).session(session))
                .andExpect(content().string(containsString("Resimler çiziliyor: 0 / 4 sahne hazır.")))
                .andExpect(content().string(containsString("Resim çiziliyor…")));
        runAll();
        mvc.perform(get("/app/stories/" + storyId).session(session))
                .andExpect(content().string(containsString("/app/stories/" + storyId + "/scenes/3/image")))
                .andExpect(content().string(containsString("Resimler yapay zekâ ile çizildi.")));
        mvc.perform(get("/app/stories/" + storyId + "/media-status").session(session))
                .andExpect(jsonPath("$.images.status").value("READY"))
                .andExpect(jsonPath("$.scenes[0].image").value("/app/stories/" + storyId + "/scenes/1/image"));
        mvc.perform(get("/app").session(session)).andExpect(content().string(containsString("class=\"book-art\"")));
        mvc.perform(get("/app/stories/" + storyId + "/scenes/1/image").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));
    }

    private long completedStory() throws Exception {
        long locationId = jdbc.queryForObject("SELECT min(id) FROM location_bibles", Long.class);
        ResultActions r = mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"childId":%d,"primaryCharacterId":%d,"sideCharacterIds":[],"locationId":%d,"theme":"SHARING",
                         "narrationStyle":"WARM","length":"SHORT","includeChild":true}""".formatted(childId, characterId, locationId)));
        long storyId = ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        text.willReturn(TEXT);
        scenes.willReturn(SCENES);
        assertThat(worker.runOnce()).isTrue();
        return storyId;
    }

    private ResultActions api(long storyId) throws Exception {
        return mvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + token));
    }

    private void runAll() {
        while (worker.runOnce()) {
            // kuyruk boşalana kadar
        }
    }

    private int credits() {
        return jdbc.queryForObject("SELECT story_credit_balance FROM credit_wallets w JOIN parents p ON p.id = w.parent_id WHERE p.email = ?",
                Integer.class, email);
    }
}
