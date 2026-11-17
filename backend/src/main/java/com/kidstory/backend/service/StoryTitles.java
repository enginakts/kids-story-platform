package com.kidstory.backend.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Masal başlığı seçimi (V17). Model tek çağrıda birkaç aday yazar; burada ek LLM çağrısı olmadan en uygun aday seçilir:
 * 1) ebeveynin diğer başlıklarıyla çakışmayan, genel olmayan ve 3-7 kelimelik aday,
 * 2) çakışmayan ve genel olmayan, 3) çakışmayan. Hepsi çakışırsa ilk adayın sonuna mekân adı eklenir ("… · Çiçekli Orman");
 * o da çakışırsa sıra numarası eklenir. Karşılaştırma büyük/küçük harf, noktalama ve boşluk farkını yok sayar ve
 * adaylar çocuğun adıyla kişiselleştirilmiş hâliyle karşılaştırılır (kayıtlı başlıklar o hâldedir).
 */
public final class StoryTitles {

    private static final Locale TR = Locale.forLanguageTag("tr");
    /** "Pamuk'un Masalı", "Leo'nun Hikâyesi" gibi genel başlıklar */
    private static final Pattern GENERIC = Pattern.compile("^\\p{L}+(['’]\\p{L}+)?\\s+(masalı|hikâyesi|hikayesi|öyküsü)$");

    private StoryTitles() {
    }

    /**
     * @param candidates   modelin adayları (yer tutucu adla, güvenlik denetiminden geçmiş)
     * @param personalize  aday → kaydedilecek hâli (çocuğun adıyla)
     * @param existing     ebeveynin diğer masallarının başlıkları
     * @param themeLabel   tema adı ("Dostluk"); tek başına başlık olamaz
     * @param locationName ayırt edici ek için mekân adı
     * @return seçilen aday (yer tutucu adlı hâli; kişiselleştirme sonra yapılır)
     */
    public static String pick(List<String> candidates, UnaryOperator<String> personalize, List<String> existing,
                              String themeLabel, String locationName) {
        Set<String> taken = existing.stream().map(StoryTitles::norm).collect(Collectors.toSet());
        java.util.Optional<String> best = candidates.stream()
                .filter(c -> !taken.contains(norm(personalize.apply(c))))
                .min(Comparator.comparingInt(c -> rank(c, themeLabel)));
        if (best.isPresent()) {
            return best.get();
        }
        String first = candidates.getFirst();
        String withPlace = first + " · " + locationName;
        if (!taken.contains(norm(personalize.apply(withPlace)))) {
            return withPlace;
        }
        for (int n = 2; ; n++) {
            String numbered = withPlace + " " + n;
            if (!taken.contains(norm(personalize.apply(numbered)))) {
                return numbered;
            }
        }
    }

    /** 0: genel değil ve 3-7 kelime; 1: genel değil; 2: genel. */
    static int rank(String title, String themeLabel) {
        if (generic(title, themeLabel)) {
            return 2;
        }
        int words = title.strip().split("\\s+").length;
        return words >= 3 && words <= 7 ? 0 : 1;
    }

    static boolean generic(String title, String themeLabel) {
        String t = norm(title);
        return t.equals(norm(themeLabel)) || title.strip().split("\\s+").length < 2
                || GENERIC.matcher(title.strip().toLowerCase(TR)).matches();
    }

    /** Karşılaştırma için: küçük harf (Türkçe), noktalama ve fazla boşluk yok. */
    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(TR).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
