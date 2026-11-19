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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adım 5: Trendyol (metin) + Gemini (sahneler), kredi, arka plan üretimi, güvenlik, kişiselleştirme ve iade. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, ScriptedLlmClient.Config.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.generation.worker-enabled=false")
class StoryFlowTests {

    /** V12: sahne metni en az 25 kelime olmalı; test sahneleri bu güvenli dolguyla uzatılır (ilk cümle aynen kalır). */
    static final String FILLER = "Güneş ılık ılık parlıyordu ve çiçekler rüzgârla nazikçe sallanıyordu. Kuşlar dalların arasında neşeyle ötüyordu. "
            + "Herkes birbirine gülümsüyordu. Gün yumuşacık ve sakin geçiyordu, her yer huzur doluydu. Minik bir kelebek de çiçekten çiçeğe uçuyordu.";

    static String padScenes(String json) {
        return json.replace(".\",\"image_prompt\"", ". " + FILLER + "\",\"image_prompt\"");
    }

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
    @Autowired com.kidstory.backend.service.UnsavedStoryCleaner cleaner;
    @Autowired com.kidstory.backend.service.MediaStorage storage;

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
                .andExpect(jsonPath("$.scenes[0].text").value("Ada'ya bir sepet çilek geldi. " + FILLER))
                .andExpect(jsonPath("$.scenes[0].imagePrompt").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT image_prompt FROM story_scenes WHERE story_id = ? AND scene_number = 1",
                String.class, storyId)).contains("a small child").contains("Style: soft watercolor").doesNotContain("Ada").doesNotContain("Kiko");
        assertThat(jdbc.queryForObject("SELECT provider_name FROM generation_jobs WHERE story_id = ? AND job_type = 'STORY_TEXT'", String.class, storyId))
                .isEqualTo("featherless+gemini");
        // Kullanım raporu: metin ve sahne çağrıları masala bağlı olarak kaydedilir (ek çağrı yok: tam 1 + 1)
        assertThat(jdbc.queryForList("SELECT provider_type || '/' || provider_name || '/' || is_success FROM ai_usage_events WHERE story_id = ? ORDER BY id",
                String.class, storyId)).containsExactly("LLM/featherless/true", "SCENE/gemini/true");
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
    void englishTextIsRejectedAndRetriedUntilTurkish() throws Exception {
        long storyId = id(request(false));
        text.willReturn("""
                BAŞLIKLAR: Pamuk and the Berries | Pamuk's Sharing Day | Four Berries

                Once upon a time there was a little bunny named Pamuk. She lived in the forest with her friends and they were very happy.

                One day they found a basket of berries. There were only four berries, but the friends shared them all together.""");
        worker.runOnce();
        // İngilizce metin reddedilir: sahne bölücüye gitmez, masal tamamlanmaz, iş yeniden denenmek üzere kuyrukta kalır
        assertThat(jobStatus(storyId)).isEqualTo("QUEUED");
        assertThat(scenes.requests).isEmpty();
        assertThat(jdbc.queryForObject("SELECT last_error FROM generation_jobs WHERE story_id = ?", String.class, storyId))
                .contains("Türkçe değil");

        retryNow(storyId);
        text.willReturn(TEXT.replace("Kiko'nun ", "").replace("Kiko'ya", "Pamuk'a").replace("Kiko'nun", "Pamuk'un"));
        scenes.willReturn(story("Pamuk'un Paylaşma Günü", "Pamuk'a bir sepet çilek geldi.", "Pamuk'un gülümsemesi parladı."));
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, storyId)).isEqualTo("COMPLETED");
        // Masal metni talimatı Türkçe yazmayı açıkça ister (V18)
        assertThat(text.requests.getLast().systemPrompt()).contains("yalnızca Türkçe yaz");
        assertThat(text.requests.getLast().userPrompt()).contains("Masalın tamamını Türkçe yaz.");
        assertThat(scenes.requests.getLast().systemPrompt()).contains("asla İngilizceye ya da başka bir dile çevirme");
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

        jdbc.update("UPDATE parents SET is_email_verified = TRUE WHERE is_email_verified = FALSE");
        mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                        .content(body(childId, presetId, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.childId").exists());
    }

    @Test
    void onlyActiveAdminCharactersCanBeUsed() throws Exception {
        // V10 öncesinde ebeveynin oluşturduğu karakter eski masallar için saklanır ama yeni masalda seçilemez.
        Long parentId = jdbc.queryForObject("SELECT id FROM parents WHERE email = ?", Long.class, email);
        Long legacy = jdbc.queryForObject("""
                INSERT INTO character_bibles (parent_id, name, species, visual_anchor_tokens, is_system_preset)
                VALUES (?, 'Eski Bulut', 'Kedi', 'cute small sky blue kitten', FALSE) RETURNING id""", Long.class, parentId);
        mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(body(childId, legacy, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.primaryCharacterId").exists());

        Long retired = jdbc.queryForObject("""
                INSERT INTO character_bibles (name, species, visual_anchor_tokens, is_system_preset, is_active)
                VALUES ('Emekli Leo', 'Aslan', 'cute small sunny yellow lion cub', TRUE, FALSE) RETURNING id""", Long.class);
        mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(body(childId, retired, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.primaryCharacterId").exists());

        jdbc.update("UPDATE location_bibles SET is_active = FALSE WHERE id = ?", locationId);
        try {
            mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content(body(childId, presetId, true)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.locationId").exists());
        } finally {
            jdbc.update("UPDATE location_bibles SET is_active = TRUE WHERE id = ?", locationId);
        }
        mvc.perform(post("/api/v1/stories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body(childId, presetId, true))).andExpect(status().isAccepted());
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
        // Yükleme ekranı sayfayı yenilemek yerine bu durumu yoklar
        mvc.perform(get(location + "/media-status").session(session)).andExpect(jsonPath("$.status").value("PENDING"));

        text.willReturn("Cesur Kiko\n\nKiko yıldızlara el salladı.\n\nAy ona gülümsedi.\n\nDostlar geldi.\n\nHerkes uyudu.");
        scenes.willReturn(story("Cesur Kiko", "Kiko yıldızlara el salladı.", "Ay ona gülümsedi."));
        worker.runOnce();
        mvc.perform(get(location).session(session)).andExpect(content().string(containsString("Cesur Ada")))
                .andExpect(content().string(containsString("Bu masalı kaydet")));
        // V16: kaydedilmeden listede görünmez; "Bu masalı kaydet" ile eklenir
        mvc.perform(get("/app/stories").session(session)).andExpect(content().string(not(containsString("Cesur Ada"))));
        mvc.perform(post(location + "/save").session(session).with(csrf()).param("title", "Cesur Ada"))
                .andExpect(redirectedUrl(location)).andExpect(flash().attributeExists("notice"));
        mvc.perform(get("/app/stories").session(session)).andExpect(content().string(containsString("Cesur Ada")));
    }

    /** Çocuk sayfası yalnızca o çocuğun masallarını gösterir; başka ebeveyn o çocuğa ulaşamaz (404). */
    @Test
    void childPageListsOnlyThatChildsStories() throws Exception {
        long storyId = id(request(true));
        jdbc.update("UPDATE stories SET saved_at = now() WHERE id = ?", storyId); // V16: listede yalnızca kaydedilmiş masal
        long otherChild = ((Number) JsonPath.read(mvc.perform(post("/api/v1/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bora\",\"age\":4}"))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));

        // Çocuk listesindeki kart çocuğun sayfasına bağlanır
        mvc.perform(get("/app/children").session(session))
                .andExpect(content().string(containsString("href=\"/app/children/" + childId + "\"")));
        mvc.perform(get("/app/children/" + childId).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Geri: Çocuklarım")))
                .andExpect(content().string(containsString("/app/stories/" + storyId + "?child=" + childId)))
                .andExpect(content().string(containsString("/app/stories/new?child=" + childId)));
        mvc.perform(get("/app/children/" + otherChild).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Henüz masal yok")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/app/stories/" + storyId + "?"))));

        // Sihirbaz çocuğu önceden seçili açar; oluşturulan masal çocuğa dönüş bilgisiyle açılır
        mvc.perform(get("/app/stories/new").param("child", String.valueOf(childId)).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"lockedChild\"")))
                .andExpect(content().string(containsString("Başka çocuk seç")));
        mvc.perform(post("/app/stories").session(session).with(csrf())
                        .param("childId", String.valueOf(childId)).param("lockedChild", String.valueOf(childId))
                        .param("primaryCharacterId", String.valueOf(presetId)).param("locationId", String.valueOf(locationId))
                        .param("theme", "COURAGE").param("narrationStyle", "BEDTIME").param("length", "SHORT").param("includeChild", "true"))
                .andExpect(redirectedUrlPattern("/app/stories/*?child=" + childId));
        mvc.perform(get("/app/stories/" + storyId).param("child", String.valueOf(childId)).session(session))
                .andExpect(content().string(containsString("href=\"/app/children/" + childId + "\"")));

        // Başka ebeveyn: çocuk sayfası ve önceden seçili sihirbaz 404
        String otherEmail = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"B","lastName":"C","consent":true}
                """.formatted(otherEmail)));
        MockHttpSession otherSession = new MockHttpSession();
        mvc.perform(post("/login").session(otherSession).with(csrf()).param("username", otherEmail).param("password", "Parola123!"));
        mvc.perform(get("/app/children/" + childId).session(otherSession)).andExpect(status().isNotFound());
        mvc.perform(get("/app/stories/new").param("child", String.valueOf(childId)).session(otherSession)).andExpect(status().isNotFound());
    }

    /** Sahne metni 25-100 kelime aralığında olmalı: kısa da aşırı uzun da reddedilir ve iş yeniden denenir; sahne sayısı değişmez. */
    @Test
    void sceneWordLimitsAreEnforced() throws Exception {
        long storyId = id(request(true));
        text.willReturn(TEXT);
        scenes.willReturn("""
                {"title":"Kısa","scenes":[
                {"text":"Kiko'ya bir sepet çilek geldi.","image_prompt":"a basket"},
                {"text":"Kiko gülümsedi.","image_prompt":"a smile"},
                {"text":"Dostlar geldi.","image_prompt":"friends"},
                {"text":"Herkes uyudu.","image_prompt":"sleeping"}]}""");
        worker.runOnce();
        assertThat(jobStatus(storyId)).isEqualTo("QUEUED");

        retryNow(storyId);
        text.willReturn(TEXT);
        String tooLong = (FILLER + " ").repeat(4).strip(); // ~132 kelime
        scenes.willReturn(story("Uzun", tooLong, "Kiko'nun gülümsemesi parladı."));
        worker.runOnce();
        assertThat(jobStatus(storyId)).isEqualTo("QUEUED");

        retryNow(storyId);
        text.willReturn(TEXT);
        scenes.willReturn(story("Uygun", "Kiko'ya bir sepet çilek geldi.", "Kiko'nun gülümsemesi parladı."));
        worker.runOnce();
        assertThat(jobStatus(storyId)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT total_scenes FROM stories WHERE id = ?", Integer.class, storyId)).isEqualTo(4);
    }

    /** V17: aynı çağrıdaki 3 adaydan ebeveynin diğer başlıklarıyla çakışmayan seçilir; hepsi doluysa mekân eklenir. */
    @Test
    void titlesAreUniquePerParentWithoutExtraCalls() throws Exception {
        jdbc.update("UPDATE credit_wallets SET story_credit_balance = 10 WHERE parent_id = (SELECT id FROM parents WHERE email = ?)", email);
        String candidates = "BAŞLIKLAR: Kiko ve Kayıp Çilek Sepeti | Kiko'nun Paylaşılan Çilekleri | Ormandaki Dört Çilek\n\n";
        String location = jdbc.queryForObject("SELECT name FROM location_bibles WHERE id = ?", String.class, locationId);
        java.util.List<String> titles = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long storyId = id(request(true));
            text.willReturn(candidates + TEXT.substring(TEXT.indexOf("\n\n") + 2));
            scenes.willReturn(story("Gemini başlığı yok sayılır", "Kiko'ya bir sepet çilek geldi.", "Kiko'nun gülümsemesi parladı."));
            worker.runOnce();
            titles.add(jdbc.queryForObject("SELECT title FROM stories WHERE id = ?", String.class, storyId));
        }
        assertThat(titles).containsExactly("Ada ve Kayıp Çilek Sepeti", "Ada'nın Paylaşılan Çilekleri", "Ormandaki Dört Çilek",
                "Ada ve Kayıp Çilek Sepeti · " + location);
        // Her masal için tam 1 metin + 1 sahne çağrısı: başlık için ek çağrı yok
        assertThat(text.requests).hasSize(4);
        assertThat(scenes.requests).hasSize(4);
        assertThat(text.requests.getFirst().systemPrompt()).contains("BAŞLIKLAR:").contains("3-7 kelime");
    }

    /** Genel başlıklar ve güvenlik denetimine takılan aday elenir. */
    @Test
    void genericAndUnsafeTitleCandidatesAreSkipped() throws Exception {
        long storyId = id(request(true));
        text.willReturn("BAŞLIKLAR: Paylaşma | Kiko'nun Masalı | Kiko ve Canavar | Kiko ve Kayıp Çilek Sepeti\n\n"
                + TEXT.substring(TEXT.indexOf("\n\n") + 2));
        scenes.willReturn(story("x", "Kiko'ya bir sepet çilek geldi.", "Kiko'nun gülümsemesi parladı."));
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT title FROM stories WHERE id = ?", String.class, storyId)).isEqualTo("Ada ve Kayıp Çilek Sepeti");
    }

    /** V16: geçici masal listede görünmez; kaydet, başlık düzenle, kaydetme ve süre dolumu. */
    @Test
    void unsavedStoriesCanBeSavedRenamedDiscardedAndExpire() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));
        long first = completed();
        mvc.perform(get("/app/children/" + childId).session(session))
                .andExpect(content().string(not(containsString("/app/stories/" + first + "?child="))))
                .andExpect(content().string(containsString("Son üretilen masal kaydedilmedi")));
        mvc.perform(get("/app/children").session(session)).andExpect(content().string(containsString("Son üretilen masal kaydedilmedi")));
        mvc.perform(get("/api/v1/stories/" + first).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.story.saved").value(false)).andExpect(jsonPath("$.story.expiresAt").exists());

        // Güvenlik denetimine takılan başlıkla kaydedilmez; uygun başlıkla kaydedilir ve listeye girer
        mvc.perform(post("/app/stories/" + first + "/save").session(session).with(csrf()).param("title", "Ada ve Canavar"))
                .andExpect(flash().attributeExists("problem"));
        assertThat(jdbc.queryForObject("SELECT saved_at IS NULL FROM stories WHERE id = ?", Boolean.class, first)).isTrue();
        mvc.perform(post("/app/stories/" + first + "/save").session(session).with(csrf()).param("title", "  Ada'nın Çilek Sepeti ")
                        .param("child", String.valueOf(childId)))
                .andExpect(redirectedUrl("/app/stories/" + first + "?child=" + childId)).andExpect(flash().attributeExists("notice"));
        mvc.perform(get("/app/children/" + childId).session(session))
                .andExpect(content().string(containsString("Ada&#39;nın Çilek Sepeti")))
                .andExpect(content().string(not(containsString("Son üretilen masal kaydedilmedi"))));
        // Başlığı sonradan düzenleme
        mvc.perform(post("/app/stories/" + first + "/title").session(session).with(csrf()).param("title", "Ada ve Mutlu Çilekler")
                        .param("back", String.valueOf(childId)))
                .andExpect(redirectedUrl("/app/children/" + childId));
        assertThat(jdbc.queryForObject("SELECT title FROM stories WHERE id = ?", String.class, first)).isEqualTo("Ada ve Mutlu Çilekler");
        // Kayıtlı masal "kaydetme" ile silinemez
        mvc.perform(post("/app/stories/" + first + "/discard").session(session).with(csrf())).andExpect(flash().attributeExists("problem"));

        // Kaydetme: geçici masal ve dosyaları silinir, çocuk sayfasına dönülür
        long second = completed();
        storage.store("stories/" + second + "/scene-1-x.png", new byte[] {1});
        mvc.perform(post("/app/stories/" + second + "/discard").session(session).with(csrf()))
                .andExpect(redirectedUrl("/app/children/" + childId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stories WHERE id = ?", Long.class, second)).isZero();
        assertThat(storage.load("stories/" + second + "/scene-1-x.png")).isEmpty();

        // Süresi dolan geçici masal okunamaz (404) ve temizlikte dosyalarıyla silinir; kayıtlı masala dokunulmaz
        long third = completed();
        storage.store("stories/" + third + "/scene-1-x.png", new byte[] {1});
        jdbc.update("UPDATE stories SET created_at = now() - interval '25 hours' WHERE id IN (?, ?)", third, first);
        mvc.perform(get("/app/stories/" + third).session(session)).andExpect(status().isNotFound());
        assertThat(cleaner.runOnce()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stories WHERE id = ?", Long.class, third)).isZero();
        assertThat(storage.load("stories/" + third + "/scene-1-x.png")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stories WHERE id = ?", Long.class, first)).isEqualTo(1);
    }

    private long completed() throws Exception {
        long storyId = id(request(true));
        text.willReturn(TEXT);
        scenes.willReturn(story("Başlık", "Kiko'ya bir sepet çilek geldi.", "Kiko'nun gülümsemesi parladı."));
        worker.runOnce();
        return storyId;
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
        return jdbc.queryForObject("SELECT status FROM generation_jobs WHERE story_id = ? AND job_type = 'STORY_TEXT'", String.class, storyId);
    }

    private static String story(String title, String first, String second) {
        return """
                {"title":"%s","scenes":[
                {"text":"%s","image_prompt":"Kiko waves at the stars"},
                {"text":"%s","image_prompt":"a bunny smiling"},
                {"text":"Dostlar el ele tutuştu.","image_prompt":"friends holding hands"},
                {"text":"Herkes huzurla uyudu.","image_prompt":"friends sleeping under the moon"}]}""".formatted(title, first, second)
                .replace(".\",\"image_prompt\"", ". " + FILLER + "\",\"image_prompt\"");
    }
}
