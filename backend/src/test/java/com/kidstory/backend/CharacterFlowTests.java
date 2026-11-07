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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adım 4: ebeveyne ait karakterler, sistem şablonları, mekânlar ve içerik kuralları. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class CharacterFlowTests {

    private static final String PIP = """
            {"name":"Pıtırcık","speciesCode":"TAVSAN","colorCode":"MAVI","outfitCode":"ATKI","accessoryCode":"GOZLUK","personalityTraits":"Meraklı ve yardımsever"}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createdCharacterGetsSystemBuiltVisualAnchor() throws Exception {
        String token = token();
        String body = create(token, PIP).andExpect(status().isCreated())
                .andExpect(jsonPath("$.species").value("Tavşan"))
                .andExpect(jsonPath("$.colorLabel").value("Mavi"))
                .andExpect(jsonPath("$.systemPreset").value(false))
                .andExpect(jsonPath("$.visualAnchorTokens").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        assertThat(jdbc.queryForObject("SELECT visual_anchor_tokens FROM character_bibles WHERE id = ?", String.class, id.longValue()))
                .isEqualTo("cute small sky blue bunny, wearing a cozy knitted scarf, with round glasses");
    }

    @Test
    void listShowsOwnCharactersThenPresets() throws Exception {
        String token = token();
        create(token, PIP);
        mvc.perform(get("/api/v1/characters").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pıtırcık"))
                .andExpect(jsonPath("$[1].systemPreset").value(true))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void parentsCannotSeeOrChangeEachOthersCharacters() throws Exception {
        String owner = token();
        String other = token();
        Number id = JsonPath.read(create(owner, PIP).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/characters").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$[*].name", not(hasItem("Pıtırcık"))));
        mvc.perform(put("/api/v1/characters/" + id).header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content(PIP)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/characters/" + id).header("Authorization", bearer(other))).andExpect(status().isNotFound());
    }

    @Test
    void systemPresetsCannotBeEditedOrDeleted() throws Exception {
        String token = token();
        Long presetId = jdbc.queryForObject("SELECT min(id) FROM character_bibles WHERE is_system_preset", Long.class);
        mvc.perform(put("/api/v1/characters/" + presetId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(PIP)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/characters/" + presetId).header("Authorization", bearer(token))).andExpect(status().isNotFound());
    }

    @Test
    void deleteDeactivatesInsteadOfRemoving() throws Exception {
        String token = token();
        Number id = JsonPath.read(create(token, PIP).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(delete("/api/v1/characters/" + id).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT is_active FROM character_bibles WHERE id = ?", Boolean.class, id.longValue())).isFalse();
        mvc.perform(get("/api/v1/characters").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].name", not(hasItem("Pıtırcık"))));
    }

    @Test
    void blacklistedWordsAreRejectedWithFieldError() throws Exception {
        String token = token();
        create(token, PIP.replace("Pıtırcık", "Canavarcık"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        create(token, PIP.replace("Meraklı ve yardımsever", "Herkesi korkunç bir sesle uyandırır"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.personalityTraits").exists());
    }

    @Test
    void shortBlacklistWordsDoNotBlockLongerWords() throws Exception {
        // "kan" kara listede; "Kanatlı" engellenmemeli.
        create(token(), PIP.replace("Pıtırcık", "Kanatlı Pıtır")).andExpect(status().isCreated());
    }

    @Test
    void characterNameCannotContainChildName() throws Exception {
        String token = token();
        mvc.perform(post("/api/v1/children").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"İpek\",\"age\":5}")).andExpect(status().isCreated());
        create(token, PIP.replace("Pıtırcık", "Tavşan ipek"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").value(containsString("çocuğunuzun adını")));
        create(token, PIP.replace("Pıtırcık", "İpekböceği")).andExpect(status().isCreated());
    }

    @Test
    void unknownOptionCodeIsRejected() throws Exception {
        create(token(), PIP.replace("TAVSAN", "EJDERHA"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.speciesCode").exists());
    }

    @Test
    void perParentLimitComesFromSystemSettings() throws Exception {
        jdbc.update("UPDATE system_settings SET setting_value = '1' WHERE setting_key = 'character.max_per_parent'");
        try {
            String token = token();
            create(token, PIP).andExpect(status().isCreated());
            create(token, PIP.replace("Pıtırcık", "Bulut")).andExpect(status().isConflict());
        } finally {
            jdbc.update("UPDATE system_settings SET setting_value = '20' WHERE setting_key = 'character.max_per_parent'");
        }
    }

    @Test
    void optionsAndLocationsAreAvailableToMobile() throws Exception {
        String token = token();
        mvc.perform(get("/api/v1/characters/options").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.species[0].code").value("TAVSAN"))
                .andExpect(jsonPath("$.species[0].label").value("Tavşan"));
        mvc.perform(get("/api/v1/locations").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Çiçekli Orman"));
    }

    @Test
    void webCharacterPagesWork() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "Parola123!"));

        mvc.perform(get("/app/characters").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hazır karakterler")))
                .andExpect(content().string(containsString("Bulut Şatosu")));
        mvc.perform(post("/app/characters").session(session).with(csrf())
                        .param("name", "Fındık").param("speciesCode", "KEDI").param("colorCode", "TURUNCU")
                        .param("outfitCode", "YOK").param("accessoryCode", "SAPKA"))
                .andExpect(redirectedUrl("/app/characters"));
        mvc.perform(get("/app/characters").session(session)).andExpect(content().string(containsString("Fındık")));

        mvc.perform(post("/app/characters").session(session).with(csrf())
                        .param("name", "Canavar").param("speciesCode", "KEDI").param("colorCode", "TURUNCU"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("çocuklar için uygun değil")));

        Long id = jdbc.queryForObject("SELECT cb.id FROM character_bibles cb JOIN parents p ON p.id = cb.parent_id WHERE p.email = ?",
                Long.class, email);
        mvc.perform(get("/app/characters/" + id + "/edit").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Fındık")));
    }

    @Test
    void staticAssetsArePublic() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/fonts/nunito-latin.woff2")).andExpect(status().isOk());
    }

    private ResultActions create(String token, String json) throws Exception {
        return mvc.perform(post("/api/v1/characters").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
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
