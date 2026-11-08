package com.kidstory.backend.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeroArtTests {

    private final HeroArt art = new HeroArt();

    @Test
    void mapsCatalogueAndPresetSpeciesToPictures() {
        assertThat(art.of("Tavşan")).isEqualTo("tavsan.webp");
        assertThat(art.of("Yavru Aslan")).isEqualTo("aslan.webp");
        assertThat(art.of("Yavru Fil")).isEqualTo("fil.webp");
        assertThat(art.of("Ayıcık")).isEqualTo("ayi.webp");
        assertThat(art.of("Baykuş")).isEqualTo("baykus.webp");
        assertThat(art.of("Robot")).isEqualTo("robot.svg");
    }

    @Test
    void unknownOrEmptySpeciesHasNoPicture() {
        assertThat(art.of("Zürafa")).isNull();
        assertThat(art.of("  ")).isNull();
        assertThat(art.of(null)).isNull();
    }
}
