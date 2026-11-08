package com.kidstory.backend.controller;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Karakter türü için ana sayfadaki iki renkli kahraman resmini bulur (static/img/heroes).
 * Şablonlarda kullanılır: th:src="@{'/img/heroes/' + ${@heroArt.of(c.species)}}". Eşleşme yoksa null döner;
 * şablon o zaman elma avatarını gösterir.
 */
@Component("heroArt")
public class HeroArt {

    private static final Locale TR = Locale.forLanguageTag("tr");
    // Sıra önemli: daha özel anahtar önce gelir ("ayıcık" "ayı"dan önce)
    private static final Map<String, String> FILES = new LinkedHashMap<>();
    static {
        FILES.put("tavşan", "tavsan.webp");
        FILES.put("aslan", "aslan.webp");
        FILES.put("fil", "fil.webp");
        FILES.put("kedi", "kedi.webp");
        FILES.put("köpek", "kopek.webp");
        FILES.put("ayı", "ayi.webp");
        FILES.put("tilki", "tilki.webp");
        FILES.put("penguen", "penguen.webp");
        FILES.put("baykuş", "baykus.webp");
        FILES.put("dinozor", "dinozor.webp");
        FILES.put("robot", "robot.svg");
    }

    public String of(String species) {
        if (species == null || species.isBlank()) {
            return null;
        }
        String s = species.toLowerCase(TR);
        for (Map.Entry<String, String> e : FILES.entrySet()) {
            for (String word : s.split("\\s+")) {
                if (word.startsWith(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}
