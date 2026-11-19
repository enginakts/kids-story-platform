package com.kidstory.backend.controller;

import com.kidstory.backend.dto.LocationResponse;
import com.kidstory.backend.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Locale;

/**
 * Giriş yapmadan görülen tanıtım sayfaları. Ana sayfa (/) AuthPageController'dadır; üst menüdeki diğer bağlantıların
 * her biri ayrı bir sayfadır. Kahramanlar ve mekânlar yönetim panelindeki kütüphaneden (yayındakiler) gelir.
 */
@Controller
@RequiredArgsConstructor
public class SitePageController {

    private final CharacterService library;

    /** Mekân paneli: mekân ve varsa adına uyan döngü videosu (static/video). */
    public record PlaceView(LocationResponse place, String video) {}

    @GetMapping("/nasil-calisir")
    String howItWorks() {
        return "site/nasil-calisir";
    }

    @GetMapping("/ornek-masal")
    String sampleStory() {
        return "site/ornek-masal";
    }

    @GetMapping("/kahramanlar")
    String characters(Model model) {
        model.addAttribute("characters", library.presets());
        return "site/kahramanlar";
    }

    @GetMapping("/mekan-ve-tema")
    String places(Model model) {
        model.addAttribute("places", library.locations().stream().map(l -> new PlaceView(l, video(l))).toList());
        return "site/mekan-ve-tema";
    }

    @GetMapping("/guvenlik")
    String safety() {
        return "site/guvenlik";
    }

    /** Hazır üç mekânın ve benzerlerinin videosu; yöneticinin eklediği başka bir mekânda örnek resim kullanılır. */
    static String video(LocationResponse l) {
        String text = (l.name() + " " + l.environmentType()).toLowerCase(Locale.forLanguageTag("tr"));
        for (String[] rule : List.of(new String[] {"bulut", "bulut"}, new String[] {"gökyüzü", "bulut"},
                new String[] {"kumsal", "dalga"}, new String[] {"deniz", "dalga"}, new String[] {"kıyı", "dalga"},
                new String[] {"orman", "dere"}, new String[] {"dere", "dere"})) {
            if (text.contains(rule[0])) {
                return rule[1];
            }
        }
        return null;
    }
}
