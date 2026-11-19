package com.kidstory.backend.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masal metninin Türkçe olup olmadığını kaba ama güvenilir biçimde denetler. Dil modelleri (özellikle İngilizce
 * düşünen modeller) ara sıra yanıtı İngilizceye kaydırabilir; böyle bir yanıt kabul edilmez ve iş yeniden denenir.
 * Ölçüt: İngilizcenin sık kullanılan ve Türkçede bulunmayan bağlaç/zamirleri ile Türkçenin sık kelimeleri ve
 * Türkçeye özgü harfler (ç ğ ı ö ş ü) karşılaştırılır. Tek tük İngilizce kelime (ör. bir ad) metni reddettirmez.
 */
public final class TurkishText {

    /** Türkçede kelime olarak geçmeyen sık İngilizce kelimeler ("her", "a" gibi iki dilde de geçenler bilerek yok). */
    private static final Set<String> ENGLISH = Set.of("the", "and", "was", "were", "with", "they", "she", "his", "their",
            "is", "are", "of", "to", "it", "that", "said", "upon", "once", "time", "there", "had", "but", "all", "very",
            "little", "happy", "friends", "day", "into", "from", "for", "then", "together");
    private static final Set<String> TURKISH = Set.of("bir", "ve", "ile", "da", "de", "çok", "gibi", "için", "ama", "sonra",
            "varmış", "yokmuş", "her", "bu", "o", "onu", "ona", "birlikte", "küçük", "mutlu");
    private static final Pattern WORD = Pattern.compile("[\\p{L}']+");
    private static final Pattern TURKISH_LETTER = Pattern.compile("[çğıöşüÇĞİÖŞÜ]");

    private TurkishText() {
    }

    /** Metin Türkçe görünüyorsa true. Boş ya da çok kısa metin için true döner (o durumu başka denetimler yakalar). */
    public static boolean looksTurkish(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        int english = 0, turkish = 0, words = 0;
        Matcher m = WORD.matcher(text.toLowerCase(Locale.forLanguageTag("tr")));
        while (m.find()) {
            String w = m.group();
            words++;
            if (ENGLISH.contains(w)) {
                english++;
            }
            if (TURKISH.contains(w)) {
                turkish++;
            }
        }
        if (words < 12) {
            return true;
        }
        int letters = 0;
        Matcher t = TURKISH_LETTER.matcher(text);
        while (t.find()) {
            letters++;
        }
        // İngilizce sözcükler belirgin (en az 4 ve kelimelerin %8'i) ve Türkçe işaretlerden fazlaysa Türkçe değildir
        boolean englishHeavy = english >= 4 && english * 100 >= words * 8;
        return !(englishHeavy && english > turkish && letters < words / 4);
    }

    /** Türkçe değilse IllegalStateException: AiRouter sıradaki sağlayıcıya geçer, olmazsa iş yeniden denenir. */
    public static void require(String text, String stage) {
        if (!looksTurkish(text)) {
            throw new IllegalStateException(stage + " Türkçe değil; yanıt reddedildi");
        }
    }
}
