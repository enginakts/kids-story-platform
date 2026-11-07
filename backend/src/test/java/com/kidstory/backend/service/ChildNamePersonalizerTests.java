package com.kidstory.backend.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChildNamePersonalizerTests {

    @ParameterizedTest
    @CsvSource({
            "Kiko'nun, Ada, Ada'nın",
            "Kiko'nun, Can, Can'ın",
            "Kiko'nun, Deniz, Deniz'in",
            "Kiko'nun, Umut, Umut'un",
            "Kiko'nun, Öykü, Öykü'nün",
            "Kiko'ya, Ada, Ada'ya",
            "Kiko'ya, Can, Can'a",
            "Kiko'ya, Elif, Elif'e",
            "Kiko'yu, Ada, Ada'yı",
            "Kiko'yu, Ömer, Ömer'i",
            "Kiko'da, Burak, Burak'ta",
            "Kiko'da, Deniz, Deniz'de",
            "Kiko'dan, Ayşe, Ayşe'den",
            "Kiko'dan, Burak, Burak'tan",
            "Kiko'yla, Ada, Ada'yla",
            "Kiko'yla, Can, Can'la",
            "Kiko’nun, Ada, Ada'nın",
            "Kiko, Ada, Ada",
    })
    void inflectsSuffixForRealName(String input, String name, String expected) {
        assertThat(ChildNamePersonalizer.apply(input, "Kiko", name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Kikolar oynadı., Kikolar oynadı.", "Minik Kiko güldü., Minik Ali güldü."})
    void replacesOnlyWholeWord(String input, String expected) {
        assertThat(ChildNamePersonalizer.apply(input, "Kiko", "Ali")).isEqualTo(expected);
    }
}
