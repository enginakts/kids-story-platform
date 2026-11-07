package com.kidstory.backend.service;

import com.kidstory.backend.exception.FieldValidationException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ebeveynin seçebileceği karakter özellikleri. Her seçeneğin Türkçe adı arayüzde, İngilizce karşılığı
 * görsel tanım metninde kullanılır. Korkutucu veya karmaşık görünüm seçeneği bilerek yoktur.
 */
public final class CharacterCatalog {

    private CharacterCatalog() {
    }

    public interface Option {
        String name();

        String label();

        String token();
    }

    public enum Species implements Option {
        TAVSAN("Tavşan", "bunny"), KEDI("Kedi", "kitten"), KOPEK("Köpek", "puppy"),
        AYI("Ayıcık", "teddy bear cub"), FIL("Fil", "baby elephant"), ASLAN("Aslan", "lion cub"),
        TILKI("Tilki", "fox cub"), PENGUEN("Penguen", "penguin chick"), BAYKUS("Baykuş", "owlet"),
        DINOZOR("Dinozor", "friendly baby dinosaur"), ROBOT("Robot", "little round robot");

        private final String label;
        private final String token;

        Species(String label, String token) {
            this.label = label;
            this.token = token;
        }

        public String label() {
            return label;
        }

        public String token() {
            return token;
        }
    }

    public enum Color implements Option {
        BEYAZ("Beyaz", "white", "#FBF8F3"), SARI("Sarı", "sunny yellow", "#FFEFA8"),
        TURUNCU("Turuncu", "soft orange", "#FFCFB0"), PEMBE("Pembe", "pastel pink", "#FFC7DD"),
        MOR("Mor", "lavender", "#DAD0FF"), MAVI("Mavi", "sky blue", "#BFE0F8"),
        YESIL("Yeşil", "mint green", "#BFEBD7"), KAHVE("Kahverengi", "light brown", "#E8D3BC"),
        GRI("Gri", "soft gray", "#E3E1EA");

        private final String label;
        private final String token;
        private final String hex;

        Color(String label, String token, String hex) {
            this.label = label;
            this.token = token;
            this.hex = hex;
        }

        public String label() {
            return label;
        }

        public String token() {
            return token;
        }

        public String hex() {
            return hex;
        }
    }

    public enum Outfit implements Option {
        YOK("Kıyafetsiz", ""), ATKI("Atkı", "wearing a cozy knitted scarf"), YELEK("Yelek", "wearing a little vest"),
        TULUM("Tulum", "wearing denim overalls"), ELBISE("Elbise", "wearing a twirly dress"),
        YAGMURLUK("Yağmurluk", "wearing a yellow raincoat"), PIJAMA("Pijama", "wearing star-patterned pajamas");

        private final String label;
        private final String token;

        Outfit(String label, String token) {
            this.label = label;
            this.token = token;
        }

        public String label() {
            return label;
        }

        public String token() {
            return token;
        }
    }

    public enum Accessory implements Option {
        YOK("Aksesuarsız", ""), SAPKA("Şapka", "with a tiny hat"), GOZLUK("Yuvarlak gözlük", "with round glasses"),
        PAPYON("Papyon", "with a bow tie"), CICEK_TACI("Çiçek tacı", "with a daisy flower crown"),
        CANTA("Sırt çantası", "with a small backpack"), FIYONK("Fiyonk", "with a hair bow");

        private final String label;
        private final String token;

        Accessory(String label, String token) {
            this.label = label;
            this.token = token;
        }

        public String label() {
            return label;
        }

        public String token() {
            return token;
        }
    }

    public static <E extends Enum<E> & Option> E parse(Class<E> type, String code, String field, E fallback) {
        if (code == null || code.isBlank()) {
            if (fallback != null) {
                return fallback;
            }
            throw new FieldValidationException(field, "Lütfen bir seçim yapın");
        }
        try {
            return Enum.valueOf(type, code.trim());
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException(field, "Geçersiz seçim");
        }
    }

    /** Görsel üretimde kullanılacak İngilizce karakter tanımı. Stil ifadesi üretim aşamasında eklenir. */
    public static String visualAnchor(Species species, Color color, Outfit outfit, Accessory accessory) {
        return Stream.of("cute small " + color.token() + " " + species.token(), outfit.token(), accessory.token())
                .filter(s -> !s.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
    }

    public record Choice(String code, String label) {
    }

    public static List<Choice> choices(Option[] values) {
        return Arrays.stream(values).map(v -> new Choice(v.name(), v.label())).toList();
    }
}
