package com.kidstory.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Masal metni dil denetimi: Türkçe masal geçer, İngilizce masal reddedilir. */
class TurkishTextTests {

    private static final String TURKISH = """
            BAŞLIKLAR: Pamuk ve Kiko'nun Çilek Bahçesi | Pamuk'un Paylaşılan Çilekleri | Ormandaki Dört Çilek

            Bir varmış, bir yokmuş, Çiçekli Orman'da Pamuk adında bir tavşan yaşarmış. Pamuk mavi atkısını takar,
            her sabah çiçekleri koklarmış. Bir gün Kiko adında küçük bir çocuk ormana gelmiş.

            Akşam olunca dostlar yumuşak çimenlere uzanmış. Pamuk, paylaştıkça mutluluğun çoğaldığını öğrenmiş.""";

    private static final String ENGLISH = """
            TITLES: Pamuk and the Strawberry Basket | Pamuk's Sharing Day | Four Berries

            Once upon a time there was a little bunny named Pamuk. She lived in the Flower Forest with her friends,
            and they were very happy together. One day Kiko came to the forest and said hello to all of them.

            In the evening the friends lay on the soft grass. Pamuk learned that sharing makes happiness grow.""";

    @Test
    void turkishStoryPasses() {
        assertThat(TurkishText.looksTurkish(TURKISH)).isTrue();
    }

    @Test
    void englishStoryIsRejected() {
        assertThat(TurkishText.looksTurkish(ENGLISH)).isFalse();
        assertThatThrownBy(() -> TurkishText.require(ENGLISH, "Masal metni"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Türkçe değil");
    }

    @Test
    void aFewEnglishWordsInATurkishStoryAreFine() {
        assertThat(TurkishText.looksTurkish(TURKISH + " Robotun adı Happy Bot'muş; onunla birlikte the end diye şarkı söylemişler."))
                .isTrue();
    }

    @Test
    void turkishWordsThatLookEnglishDoNotCount() {
        // "her", "on", "o" ve "a" Türkçede de geçer
        assertThat(TurkishText.looksTurkish("Her sabah on tavşan çayıra gelirmiş. O gün a harfini öğrenmişler; her biri çok mutluymuş ve birlikte oynamışlar."))
                .isTrue();
    }

    @Test
    void shortOrEmptyTextIsLeftToOtherChecks() {
        assertThat(TurkishText.looksTurkish("")).isTrue();
        assertThat(TurkishText.looksTurkish("The end.")).isTrue();
    }
}
