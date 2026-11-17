package com.kidstory.backend.controller;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.dto.StoryOptionsResponse;
import com.kidstory.backend.dto.StoryRequest;
import com.kidstory.backend.dto.StorySummaryResponse;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.EmailNotVerifiedException;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.BillingService;
import com.kidstory.backend.service.CharacterService;
import com.kidstory.backend.service.ChildProfileService;
import com.kidstory.backend.service.StoryAudioService;
import com.kidstory.backend.service.StoryImageService;
import com.kidstory.backend.service.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class StoryPageController {

    private final StoryService stories;
    private final StoryAudioService audio;
    private final StoryImageService images;
    private final BillingService billing;
    private final ChildProfileService children;
    private final CharacterService characters;

    /** Uygulamanın ana yolu: ana ekran çocuk listesidir. */
    @GetMapping("/app")
    String home() {
        return "redirect:/app/children";
    }

    /** Tüm masallar (eski ana ekran, "Kütüphanem"). */
    @GetMapping("/app/stories")
    String dashboard(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("emailVerified", stories.emailVerified(user.id()));
        model.addAttribute("wallet", billing.wallet(user.id()).orElse(null));
        model.addAttribute("stories", stories.list(user.id()));
        return "app/dashboard";
    }

    /** Sihirbaz. ?child=ID ile gelinirse o çocuk önceden seçilidir (çocuk bu ebeveynin değilse 404). */
    @GetMapping("/app/stories/new")
    String wizard(@AuthenticationPrincipal AppUser user,
                  @RequestParam(required = false) Long child, Model model) {
        if (child != null) model.addAttribute("lockedChild", ownedChild(user, child));
        model.addAttribute("form", new StoryRequest(child, null, List.of(), null, StoryEnums.Theme.SHARING,
                StoryEnums.NarrationStyle.WARM, StoryEnums.Length.MEDIUM, true));
        return wizardPage(user, model);
    }

    private ChildProfileResponse ownedChild(AppUser user, Long childId) {
        try {
            return children.get(user.id(), childId);
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/app/stories")
    String create(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") StoryRequest form,
                  BindingResult result,
                  @RequestParam(required = false) Long lockedChild, Model model) {
        if (lockedChild != null) model.addAttribute("lockedChild", ownedChild(user, lockedChild));
        if (!result.hasErrors()) {
            try {
                StorySummaryResponse story = stories.request(user.id(), form);
                // Çocuk sayfasından gelindiyse oynatıcının geri bağlantısı o çocuğa döner
                return "redirect:/app/stories/" + story.id() + (lockedChild != null ? "?child=" + lockedChild : "");
            } catch (FieldValidationException e) {
                result.rejectValue(e.getField(), "rule", e.getMessage());
            } catch (ConflictException | EmailNotVerifiedException e) {
                model.addAttribute("formError", e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                model.addAttribute("formError", "İşlem aynı anda başka bir istekle çakıştı. Lütfen tekrar deneyin.");
            }
        }
        return wizardPage(user, model);
    }

    @GetMapping("/app/stories/{id}")
    String story(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                 @RequestParam(required = false) Long child, Model model) {
        // ?child=ID: geri bağlantısı o çocuğun sayfasına döner (çocuk bu ebeveynin değilse yok sayılır)
        if (child != null) {
            try { model.addAttribute("backChild", children.get(user.id(), child)); }
            catch (NotFoundException ignored) { }
        }
        model.addAttribute("user", user);
        StoryDetailResponse detail = found(() -> stories.get(user.id(), id));
        model.addAttribute("detail", detail);
        if (detail.story().expiresAt() != null) {
            model.addAttribute("expiresText", java.time.format.DateTimeFormatter.ofPattern("d MMMM HH:mm", java.util.Locale.forLanguageTag("tr"))
                    .format(detail.story().expiresAt().atZone(java.time.ZoneId.of("Europe/Istanbul"))));
        }
        return "app/story";
    }

    /** Okuyucunun sahne sesi. Oturumdaki ebeveynin masalı değilse 404. */
    @GetMapping("/app/stories/{id}/scenes/{number}/audio")
    ResponseEntity<Resource> sceneAudio(@AuthenticationPrincipal AppUser user, @PathVariable Long id, @PathVariable int number) {
        return StoryController.audioResponse(audio.file(user.id(), id, number));
    }

    @GetMapping("/app/stories/{id}/scenes/{number}/image")
    ResponseEntity<Resource> sceneImage(@AuthenticationPrincipal AppUser user, @PathVariable Long id, @PathVariable int number) {
        StoryImageService.ImageFile file = images.file(user.id(), id, number);
        return StoryController.media(file.resource(), file.mimeType());
    }

    /**
     * Okuyucu, resim veya ses hazırlanırken bu durumu yoklar ve hazır olan sahneleri sayfayı yenilemeden açar.
     * Adresler web oturumuna göredir (mobil API'nin adresleri değil).
     */
    @GetMapping("/app/stories/{id}/media-status")
    @ResponseBody
    Map<String, Object> mediaStatus(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        StoryDetailResponse detail = found(() -> stories.get(user.id(), id));
        List<Map<String, Object>> list = detail.scenes().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sceneNumber", s.sceneNumber());
                    m.put("image", s.imageUrl() == null ? null : "/app/stories/" + id + "/scenes/" + s.sceneNumber() + "/image");
                    m.put("audio", s.audioUrl() == null ? null : "/app/stories/" + id + "/scenes/" + s.sceneNumber() + "/audio");
                    return m;
                })
                .toList();
        // status: yükleme ekranı bunu yoklar; masal hazır ya da başarısız olunca sayfayı bir kez yeniler
        return Map.of("status", detail.story().status(), "audio", detail.audio(), "images", detail.images(), "scenes", list);
    }

    @PostMapping("/app/stories/{id}/images")
    String requestImages(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                  @RequestParam(required = false) Long child) {
        images.requestImages(user.id(), id);
        return "redirect:/app/stories/" + id + (child != null ? "?child=" + child : "");
    }

    @PostMapping("/app/stories/{id}/audio")
    String requestAudio(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                  @RequestParam(required = false) Long child) {
        audio.requestAudio(user.id(), id);
        return "redirect:/app/stories/" + id + (child != null ? "?child=" + child : "");
    }

    // ---------- Kaydet / kaydetme / başlık (V16) ----------

    /** "Bu masalı kaydet": kota sunucuda denetlenir; başlık düzeltilebilir. */
    @PostMapping("/app/stories/{id}/save")
    String save(@AuthenticationPrincipal AppUser user, @PathVariable Long id, @RequestParam(required = false) String title,
                @RequestParam(required = false) Long child, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try {
            StorySummaryResponse saved = found(() -> stories.save(user.id(), id, title));
            redirect.addFlashAttribute("notice", "Kaydedildi. Masal " + saved.childName() + " için masallar arasına eklendi.");
        } catch (FieldValidationException | ConflictException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/app/stories/" + id + (child != null ? "?child=" + child : "");
    }

    /** "Kaydetme": geçici masal silinir ve çocuğun sayfasına dönülür (5 sn'lik "Geri al" tarayıcıda, gönderimden önce). */
    @PostMapping("/app/stories/{id}/discard")
    String discard(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try {
            Long childId = found(() -> stories.discard(user.id(), id));
            redirect.addFlashAttribute("notice", "Masal kaydedilmedi ve silindi.");
            return childId == null ? "redirect:/app/children" : "redirect:/app/children/" + childId;
        } catch (ConflictException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
            return "redirect:/app/stories/" + id;
        }
    }

    /** Masal kartındaki "Başlığı düzenle". back: dönülecek çocuk sayfası (yoksa tüm masallar). */
    @PostMapping("/app/stories/{id}/title")
    String rename(@AuthenticationPrincipal AppUser user, @PathVariable Long id, @RequestParam(required = false) String title,
                  @RequestParam(required = false) Long back, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try {
            found(() -> { stories.rename(user.id(), id, title); return null; });
            redirect.addFlashAttribute("notice", "Başlık güncellendi.");
        } catch (FieldValidationException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return back != null ? "redirect:/app/children/" + back : "redirect:/app/stories";
    }

    /** Web sayfalarında "bulunamadı" 404 olsun (500 değil); süresi dolmuş geçici masal da buna girer. */
    private static <T> T found(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private String wizardPage(AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("emailVerified", stories.emailVerified(user.id()));
        model.addAttribute("wallet", billing.wallet(user.id()).orElse(null));
        model.addAttribute("children", children.list(user.id()));
        model.addAttribute("presets", characters.presets());
        model.addAttribute("locations", characters.locations());
        model.addAttribute("options", StoryOptionsResponse.create());
        return "app/story-new";
    }
}
