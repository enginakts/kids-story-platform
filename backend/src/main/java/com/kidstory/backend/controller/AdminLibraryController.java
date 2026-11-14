package com.kidstory.backend.controller;

import com.kidstory.backend.dto.CharacterOptionsResponse;
import com.kidstory.backend.dto.CharacterRequest;
import com.kidstory.backend.dto.CharacterResponse;
import com.kidstory.backend.dto.LocationForm;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.CharacterService;
import com.kidstory.backend.service.LibraryImageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Yönetim paneli: karakter ve mekân kütüphanesi. Bütün yöneticiler kullanır. Burada yayına alınan karakter ve
 * mekânlar bütün ebeveynlerin masal sihirbazında, yüklenen örnek resimleriyle görünür; ebeveynler değiştiremez.
 * Kayıtlar silinmez (eski masallar bağlıdır), yayından kaldırılır.
 * Yeni kayıt formu liste sayfasında, "Yeni … oluştur" düğmesiyle açılan bir alandadır (formOpen).
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminLibraryController {

    private final CharacterService library;
    private final LibraryImageService images;

    @ModelAttribute
    void common(@AuthenticationPrincipal AppUser user, Model model) {
        if (user != null) {
            model.addAttribute("user", user);
            model.addAttribute("superAdmin", Role.SUPER_ADMIN.equals(user.role()));
        }
        model.addAttribute("maxImages", LibraryImageService.MAX_IMAGES);
    }

    // ---------- Karakterler ----------

    @GetMapping("/characters")
    String characters(Model model) {
        model.addAttribute("form", new CharacterRequest("", "", "", "YOK", "YOK", ""));
        return charactersPage(model, false);
    }

    @PostMapping("/characters")
    String createCharacter(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") CharacterRequest form,
                           BindingResult result, @RequestParam(name = "images", required = false) List<MultipartFile> files,
                           HttpServletRequest request, Model model, RedirectAttributes redirect) {
        LibraryImageService.Prepared pictures = prepare(files, model);
        if (!result.hasErrors() && pictures != null) {
            try {
                CharacterResponse created = library.createCharacter(user.id(), form, pictures, request.getRemoteAddr());
                redirect.addFlashAttribute("notice", created.name() + " eklendi ve bütün ebeveynlere açıldı.");
                return "redirect:/admin/characters";
            } catch (FieldValidationException e) {
                reject(result, model, e);
            }
        }
        return charactersPage(model, true);
    }

    @GetMapping("/characters/{id}")
    String editCharacter(@PathVariable long id, Model model) {
        CharacterResponse c = library.character(id);
        model.addAttribute("form", new CharacterRequest(c.name(), c.speciesCode(), c.colorCode(),
                c.outfitCode() == null ? "YOK" : c.outfitCode(), c.accessoryCode() == null ? "YOK" : c.accessoryCode(),
                c.personalityTraits()));
        return characterPage(id, c.name(), model);
    }

    @PostMapping("/characters/{id}")
    String updateCharacter(@AuthenticationPrincipal AppUser user, @PathVariable long id,
                           @Valid @ModelAttribute("form") CharacterRequest form, BindingResult result,
                           @RequestParam(name = "images", required = false) List<MultipartFile> files,
                           @RequestParam(name = "removeImageIds", required = false) List<Long> removeImageIds,
                           HttpServletRequest request, Model model, RedirectAttributes redirect) {
        LibraryImageService.Prepared pictures = prepare(files, model);
        if (!result.hasErrors() && pictures != null) {
            try {
                library.updateCharacter(user.id(), id, form, pictures, removeImageIds, request.getRemoteAddr());
                redirect.addFlashAttribute("notice", form.name().trim() + " güncellendi.");
                return "redirect:/admin/characters";
            } catch (FieldValidationException e) {
                reject(result, model, e);
            }
        }
        return characterPage(id, library.character(id).name(), model);
    }

    @PostMapping("/characters/{id}/active")
    String characterActive(@AuthenticationPrincipal AppUser user, @PathVariable long id, @RequestParam boolean active,
                           HttpServletRequest request, RedirectAttributes redirect) {
        library.setCharacterActive(user.id(), id, active, request.getRemoteAddr());
        redirect.addFlashAttribute("notice", active ? "Karakter yeniden yayında." : "Karakter yayından kaldırıldı; eski masallar etkilenmez.");
        return "redirect:/admin/characters";
    }

    private String charactersPage(Model model, boolean formOpen) {
        model.addAttribute("characters", library.allCharacters());
        model.addAttribute("options", CharacterOptionsResponse.create());
        model.addAttribute("formOpen", formOpen);
        return "admin/characters";
    }

    private String characterPage(long id, String name, Model model) {
        model.addAttribute("characterId", id);
        model.addAttribute("characterName", name);
        model.addAttribute("existingImages", library.characterImages(id));
        model.addAttribute("options", CharacterOptionsResponse.create());
        return "admin/character";
    }

    // ---------- Mekânlar ----------

    @GetMapping("/locations")
    String locations(Model model) {
        model.addAttribute("form", LocationForm.empty());
        return locationsPage(model, false);
    }

    @PostMapping("/locations")
    String createLocation(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") LocationForm form,
                          BindingResult result, @RequestParam(name = "images", required = false) List<MultipartFile> files,
                          HttpServletRequest request, Model model, RedirectAttributes redirect) {
        LibraryImageService.Prepared pictures = prepare(files, model);
        if (!result.hasErrors() && pictures != null) {
            try {
                library.createLocation(user.id(), form, pictures, request.getRemoteAddr());
                redirect.addFlashAttribute("notice", form.name().trim() + " eklendi ve bütün ebeveynlere açıldı.");
                return "redirect:/admin/locations";
            } catch (FieldValidationException e) {
                reject(result, model, e);
            }
        }
        return locationsPage(model, true);
    }

    @GetMapping("/locations/{id}")
    String editLocation(@PathVariable long id, Model model) {
        model.addAttribute("form", library.locationForm(id));
        return locationPage(id, model);
    }

    @PostMapping("/locations/{id}")
    String updateLocation(@AuthenticationPrincipal AppUser user, @PathVariable long id,
                          @Valid @ModelAttribute("form") LocationForm form, BindingResult result,
                          @RequestParam(name = "images", required = false) List<MultipartFile> files,
                          @RequestParam(name = "removeImageIds", required = false) List<Long> removeImageIds,
                          HttpServletRequest request, Model model, RedirectAttributes redirect) {
        LibraryImageService.Prepared pictures = prepare(files, model);
        if (!result.hasErrors() && pictures != null) {
            try {
                library.updateLocation(user.id(), id, form, pictures, removeImageIds, request.getRemoteAddr());
                redirect.addFlashAttribute("notice", form.name().trim() + " güncellendi. Yeni masallardaki resimler bu bilgilerle çizilir.");
                return "redirect:/admin/locations";
            } catch (FieldValidationException e) {
                reject(result, model, e);
            }
        }
        return locationPage(id, model);
    }

    @PostMapping("/locations/{id}/active")
    String locationActive(@AuthenticationPrincipal AppUser user, @PathVariable long id, @RequestParam boolean active,
                          HttpServletRequest request, RedirectAttributes redirect) {
        library.setLocationActive(user.id(), id, active, request.getRemoteAddr());
        redirect.addFlashAttribute("notice", active ? "Mekân yeniden yayında." : "Mekân yayından kaldırıldı; eski masallar etkilenmez.");
        return "redirect:/admin/locations";
    }

    private String locationsPage(Model model, boolean formOpen) {
        model.addAttribute("locations", library.allLocations());
        model.addAttribute("formOpen", formOpen);
        return "admin/locations";
    }

    private String locationPage(long id, Model model) {
        model.addAttribute("locationId", id);
        model.addAttribute("locationName", library.locationName(id));
        model.addAttribute("existingImages", library.locationImages(id));
        return "admin/location";
    }

    // ---------- Ortak ----------

    /** Resimleri doğrular; hata varsa modele "imageError" yazar ve null döner (form alanları da ayrıca denetlenir). */
    private LibraryImageService.Prepared prepare(List<MultipartFile> files, Model model) {
        try {
            return images.prepare(files);
        } catch (FieldValidationException e) {
            model.addAttribute("imageError", e.getMessage());
            return null;
        }
    }

    private static void reject(BindingResult result, Model model, FieldValidationException e) {
        if ("images".equals(e.getField())) {
            model.addAttribute("imageError", e.getMessage());
        } else {
            result.rejectValue(e.getField(), "rule", e.getMessage());
        }
    }
}
