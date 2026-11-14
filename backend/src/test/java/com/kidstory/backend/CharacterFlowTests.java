package com.kidstory.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Karakter ve mekân kütüphanesi (V10): yöneticiler ekler ve düzenler, yayındakiler bütün ebeveynlere görünür;
 * ebeveynler değiştiremez, yalnızca masal oluştururken seçer. İçerik kuralları yönetici formunda da geçerlidir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class CharacterFlowTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminCharacterIsVisibleToEveryParentWithSystemBuiltVisualAnchor() throws Exception {
        String name = unique("Pıtırcık");
        MockHttpSession admin = adminSession();
        createCharacter(admin, name, "TAVSAN", "MAVI", "ATKI", "GOZLUK").andExpect(redirectedUrl("/admin/characters"));

        assertThat(jdbc.queryForObject("SELECT visual_anchor_tokens FROM character_bibles WHERE name = ?", String.class, name))
                .isEqualTo("cute small sky blue bunny, wearing a cozy knitted scarf, with round glasses");
        assertThat(jdbc.queryForObject("SELECT parent_id IS NULL AND is_system_preset FROM character_bibles WHERE name = ?",
                Boolean.class, name)).isTrue();
        for (String token : new String[] {token(), token()}) {
            mvc.perform(get("/api/v1/characters").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", hasItem(name)))
                    .andExpect(jsonPath("$[0].systemPreset").value(true))
                    .andExpect(jsonPath("$[0].visualAnchorTokens").doesNotExist());
        }
        mvc.perform(get("/admin/characters").session(admin)).andExpect(content().string(containsString(name)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs WHERE action = 'CHARACTER_CREATED' AND details LIKE ?",
                Integer.class, name + "%")).isEqualTo(1);
    }

    @Test
    void parentsCannotCreateChangeOrDeleteCharacters() throws Exception {
        String token = token();
        Long presetId = jdbc.queryForObject("SELECT min(id) FROM character_bibles WHERE is_system_preset", Long.class);
        String body = "{\"name\":\"Bulut\",\"speciesCode\":\"KEDI\",\"colorCode\":\"MAVI\"}";
        mvc.perform(post("/api/v1/characters").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/v1/characters/" + presetId).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        mvc.perform(delete("/api/v1/characters/" + presetId).header("Authorization", bearer(token)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));

        MockHttpSession parent = parentSession();
        mvc.perform(get("/app/characters").session(parent)).andExpect(status().isNotFound());
        mvc.perform(get("/app/stories/new").session(parent))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("/app/characters"))));
        // Ebeveyn oturumu yönetim paneline giremez
        mvc.perform(get("/admin/characters").session(parent))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        mvc.perform(post("/admin/characters").session(parent).with(csrf()).param("name", "Sızma").param("speciesCode", "KEDI").param("colorCode", "MAVI"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM character_bibles WHERE name = 'Sızma'", Integer.class)).isZero();
    }

    @Test
    void deactivatedCharacterDisappearsFromParentsButIsKept() throws Exception {
        String name = unique("Fındık");
        MockHttpSession admin = adminSession();
        createCharacter(admin, name, "KEDI", "TURUNCU", "YOK", "SAPKA");
        Long id = jdbc.queryForObject("SELECT id FROM character_bibles WHERE name = ?", Long.class, name);

        mvc.perform(post("/admin/characters/" + id + "/active").session(admin).with(csrf()).param("active", "false"))
                .andExpect(redirectedUrl("/admin/characters"));
        assertThat(jdbc.queryForObject("SELECT is_active FROM character_bibles WHERE id = ?", Boolean.class, id)).isFalse();
        mvc.perform(get("/api/v1/characters").header("Authorization", bearer(token())))
                .andExpect(jsonPath("$[*].name", not(hasItem(name))));

        mvc.perform(post("/admin/characters/" + id + "/active").session(admin).with(csrf()).param("active", "true"));
        mvc.perform(get("/api/v1/characters").header("Authorization", bearer(token())))
                .andExpect(jsonPath("$[*].name", hasItem(name)));
    }

    @Test
    void changingAppearanceResetsReferencePortrait() throws Exception {
        String name = unique("Pofuduk");
        MockHttpSession admin = adminSession();
        createCharacter(admin, name, "AYI", "KAHVE", "YOK", "YOK");
        Long id = jdbc.queryForObject("SELECT id FROM character_bibles WHERE name = ?", Long.class, name);
        jdbc.update("UPDATE character_bibles SET reference_image_url = 'characters/x.jpg', base_seed = 7 WHERE id = ?", id);

        // Yalnızca kişilik değişirse portre korunur
        mvc.perform(post("/admin/characters/" + id).session(admin).with(csrf()).param("name", name).param("speciesCode", "AYI")
                .param("colorCode", "KAHVE").param("outfitCode", "YOK").param("accessoryCode", "YOK").param("personalityTraits", "Uykucu"))
                .andExpect(redirectedUrl("/admin/characters"));
        assertThat(jdbc.queryForObject("SELECT reference_image_url FROM character_bibles WHERE id = ?", String.class, id)).isEqualTo("characters/x.jpg");

        // Görünüm değişirse yeni portre çizilir
        mvc.perform(post("/admin/characters/" + id).session(admin).with(csrf()).param("name", name).param("speciesCode", "AYI")
                .param("colorCode", "PEMBE").param("outfitCode", "PIJAMA").param("accessoryCode", "YOK"))
                .andExpect(redirectedUrl("/admin/characters"));
        assertThat(jdbc.queryForObject("SELECT reference_image_url FROM character_bibles WHERE id = ?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT visual_anchor_tokens FROM character_bibles WHERE id = ?", String.class, id))
                .isEqualTo("cute small pastel pink teddy bear cub, wearing star-patterned pajamas");
    }

    @Test
    void contentRulesApplyToAdminForms() throws Exception {
        MockHttpSession admin = adminSession();
        createCharacter(admin, "Canavarcık", "KEDI", "MAVI", "YOK", "YOK")
                .andExpect(status().isOk()).andExpect(content().string(containsString("çocuklar için uygun değil")));
        createCharacter(admin, unique("Ejder"), "EJDERHA", "MAVI", "YOK", "YOK")
                .andExpect(status().isOk()).andExpect(content().string(containsString("Geçersiz seçim")));
        // "kan" kara listede; "Kanatlı" engellenmemeli.
        createCharacter(admin, unique("Kanatlı Pıtır"), "BAYKUS", "MOR", "YOK", "YOK").andExpect(redirectedUrl("/admin/characters"));
        // Aynı ad ikinci kez eklenemez
        String name = unique("Tekil");
        createCharacter(admin, name, "KEDI", "MAVI", "YOK", "YOK").andExpect(redirectedUrl("/admin/characters"));
        createCharacter(admin, name, "KOPEK", "SARI", "YOK", "YOK")
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bu adla bir karakter zaten var")));
    }

    @Test
    void adminLocationIsVisibleToParentsAndCanBeDeactivated() throws Exception {
        String name = unique("Deniz Feneri");
        MockHttpSession admin = adminSession();
        createLocation(admin, name).andExpect(redirectedUrl("/admin/locations"));
        Long id = jdbc.queryForObject("SELECT id FROM location_bibles WHERE name = ?", Long.class, name);
        assertThat(jdbc.queryForObject("SELECT lighting_style FROM location_bibles WHERE id = ?", String.class, id)).isEqualTo("golden evening light");

        String token = token();
        mvc.perform(get("/api/v1/locations").header("Authorization", bearer(token))).andExpect(jsonPath("$[*].name", hasItem(name)));
        mvc.perform(get("/admin/locations/" + id).session(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("a tall striped lighthouse")));

        mvc.perform(post("/admin/locations/" + id + "/active").session(admin).with(csrf()).param("active", "false"))
                .andExpect(redirectedUrl("/admin/locations"));
        mvc.perform(get("/api/v1/locations").header("Authorization", bearer(token))).andExpect(jsonPath("$[*].name", not(hasItem(name))));

        createLocation(admin, unique("Canavar Mağarası")).andExpect(status().isOk())
                .andExpect(content().string(containsString("çocuklar için uygun değil")));
    }

    @Test
    void seededLocationsAndCharactersAreAvailable() throws Exception {
        String token = token();
        mvc.perform(get("/api/v1/locations").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].name", hasItem("Çiçekli Orman")));
        mvc.perform(get("/api/v1/characters").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].name", hasItem("Pamuk")));
        assertThat(jdbc.queryForObject("SELECT lighting_style FROM location_bibles WHERE name = 'Çiçekli Orman'", String.class))
                .isEqualTo("soft warm morning sunlight through the leaves");
    }

    @Test
    void sampleImagesAreRequiredValidatedAndShownToParents() throws Exception {
        MockHttpSession admin = adminSession();
        // Resimsiz yeni karakter eklenemez
        String bare = unique("Resimsiz");
        mvc.perform(multipart("/admin/characters").session(admin).with(csrf()).param("name", bare).param("speciesCode", "KEDI")
                        .param("colorCode", "MAVI").param("outfitCode", "YOK").param("accessoryCode", "YOK"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("En az bir örnek resim")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM character_bibles WHERE name = ?", Integer.class, bare)).isZero();
        // Resim olmayan dosya ve çok küçük resim reddedilir; kayıt oluşmaz
        String bad = unique("Bozuk");
        createCharacter(admin, bad, "KEDI", "MAVI", "YOK", "YOK",
                new MockMultipartFile("images", "not.png", "image/png", "merhaba".getBytes()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Yalnızca JPEG ya da PNG")));
        createCharacter(admin, bad, "KEDI", "MAVI", "YOK", "YOK", png("minik.png", 50, 50))
                .andExpect(status().isOk()).andExpect(content().string(containsString("çok küçük")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM character_bibles WHERE name = ?", Integer.class, bad)).isZero();

        // İki resimle eklenen karakter ebeveyne resimleriyle listelenir; resim oturumsuz açılır ve JPEG'e çevrilmiştir
        String name = unique("Resimli");
        createCharacter(admin, name, "TILKI", "TURUNCU", "YOK", "YOK", png("a.png", 1600, 1200), png("b.png", 400, 400))
                .andExpect(redirectedUrl("/admin/characters"));
        Long id = jdbc.queryForObject("SELECT id FROM character_bibles WHERE name = ?", Long.class, name);
        String body = mvc.perform(get("/api/v1/characters").header("Authorization", bearer(token())))
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> urls = JsonPath.read(body, "$[?(@.id == " + id + ")].images[*]");
        assertThat(urls).hasSize(2).allMatch(u -> u.matches("/library/images/\\d+"));
        byte[] jpeg = mvc.perform(get(urls.getFirst()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(1200);
        mvc.perform(get("/library/images/999999")).andExpect(status().isNotFound());

        // Son resim kaldırılamaz; yeni resim eklenip eskisi kaldırılabilir; en fazla 4 resim
        Long first = jdbc.queryForObject("SELECT min(id) FROM library_images WHERE character_id = ?", Long.class, id);
        Long second = jdbc.queryForObject("SELECT max(id) FROM library_images WHERE character_id = ?", Long.class, id);
        mvc.perform(multipart("/admin/characters/" + id).session(admin).with(csrf()).param("name", name).param("speciesCode", "TILKI")
                        .param("colorCode", "TURUNCU").param("removeImageIds", first.toString(), second.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("En az bir örnek resim gerekli")));
        mvc.perform(multipart("/admin/characters/" + id).file(png("c.png", 500, 500)).session(admin).with(csrf()).param("name", name)
                        .param("speciesCode", "TILKI").param("colorCode", "TURUNCU").param("removeImageIds", first.toString()))
                .andExpect(redirectedUrl("/admin/characters"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_images WHERE character_id = ?", Integer.class, id)).isEqualTo(2);
        mvc.perform(multipart("/admin/characters/" + id).file(png("d.png", 300, 300)).file(png("e.png", 300, 300))
                        .file(png("f.png", 300, 300)).session(admin).with(csrf()).param("name", name)
                        .param("speciesCode", "TILKI").param("colorCode", "TURUNCU"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("En fazla 4 örnek resim")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_images WHERE character_id = ?", Integer.class, id)).isEqualTo(2);
        mvc.perform(get("/admin/characters/" + id).session(admin)).andExpect(content().string(containsString("Kapak")));
    }

    @Test
    void locationSampleImageIsShownInWizard() throws Exception {
        String name = unique("Resimli Koy");
        MockHttpSession admin = adminSession();
        createLocation(admin, name).andExpect(redirectedUrl("/admin/locations"));
        mvc.perform(get("/api/v1/locations").header("Authorization", bearer(token())))
                .andExpect(jsonPath("$[?(@.name == '" + name + "')].images[0]", hasItem(matchesPattern("/library/images/\\d+"))))
                .andExpect(jsonPath("$[0].lightingStyle").doesNotExist());
        // Sihirbaz seçenekleri çocuk profili olan ebeveyne gösterilir
        MockHttpSession parent = parentSession();
        mvc.perform(post("/app/children").session(parent).with(csrf()).param("name", "Ada").param("age", "5"));
        mvc.perform(get("/app/stories/new").session(parent))
                .andExpect(status().isOk()).andExpect(content().string(containsString(name + " örnek resmi")));
    }

    @Test
    void staticAssetsArePublic() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/fonts/nunito-latin.woff2")).andExpect(status().isOk());
    }

    private ResultActions createCharacter(MockHttpSession admin, String name, String species, String color, String outfit,
                                          String accessory, MockMultipartFile... files) throws Exception {
        var request = multipart("/admin/characters");
        for (MockMultipartFile f : files.length == 0 ? new MockMultipartFile[] {png("ornek.png", 400, 300)} : files) {
            request.file(f);
        }
        return mvc.perform(request.session(admin).with(csrf()).param("name", name).param("speciesCode", species)
                .param("colorCode", color).param("outfitCode", outfit).param("accessoryCode", accessory)
                .param("personalityTraits", "Meraklı ve yardımsever"));
    }

    private ResultActions createLocation(MockHttpSession admin, String name) throws Exception {
        var request = multipart("/admin/locations").file(png("koy.png", 640, 480))
                .session(admin).with(csrf()).param("name", name)
                .param("environmentType", "Deniz kıyısı").param("atmosphereDescription", "Dalgaların hışırdadığı sakin bir koy")
                .param("visualAnchorTokens", "a tall striped lighthouse on a grassy cliff, calm sea")
                .param("lightingStyle", "golden evening light").param("recurringLandmarks", "a red rowing boat");
        return mvc.perform(request);
    }

    /** Test resmi: düz renkli PNG. */
    private static MockMultipartFile png(String name, int w, int h) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(242, 68, 5));
            g.fillRect(0, 0, w, h);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return new MockMultipartFile("images", name, "image/png", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String unique(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    private MockHttpSession adminSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/admin/login").session(session).with(csrf()).param("username", "testadmin").param("password", "TestAdmin123!"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/admin"));
        return session;
    }

    private MockHttpSession parentSession() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));
        return session;
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Parola123!","firstName":"Ayşe","lastName":"Kaya","consent":true}
                """.formatted(email)));
    }

    private String token() throws Exception {
        return JsonPath.read(register(UUID.randomUUID() + "@example.com").andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
