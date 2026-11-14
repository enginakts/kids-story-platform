package com.kidstory.backend.controller;

import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.dto.StoryOptionsResponse;
import com.kidstory.backend.dto.StoryRequest;
import com.kidstory.backend.dto.StorySummaryResponse;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.EmailNotVerifiedException;
import com.kidstory.backend.exception.FieldValidationException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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

    @GetMapping("/app")
    String dashboard(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("emailVerified", stories.emailVerified(user.id()));
        model.addAttribute("wallet", billing.wallet(user.id()).orElse(null));
        model.addAttribute("stories", stories.list(user.id()));
        return "app/dashboard";
    }

    @GetMapping("/app/stories/new")
    String wizard(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("form", new StoryRequest(null, null, List.of(), null, StoryEnums.Theme.SHARING,
                StoryEnums.NarrationStyle.WARM, StoryEnums.Length.MEDIUM, true));
        return wizardPage(user, model);
    }

    @PostMapping("/app/stories")
    String create(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") StoryRequest form,
                  BindingResult result, Model model) {
        if (!result.hasErrors()) {
            try {
                StorySummaryResponse story = stories.request(user.id(), form);
                return "redirect:/app/stories/" + story.id();
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
    String story(@AuthenticationPrincipal AppUser user, @PathVariable Long id, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("detail", stories.get(user.id(), id));
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
        StoryDetailResponse detail = stories.get(user.id(), id);
        List<Map<String, Object>> list = detail.scenes().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sceneNumber", s.sceneNumber());
                    m.put("image", s.imageUrl() == null ? null : "/app/stories/" + id + "/scenes/" + s.sceneNumber() + "/image");
                    m.put("audio", s.audioUrl() == null ? null : "/app/stories/" + id + "/scenes/" + s.sceneNumber() + "/audio");
                    return m;
                })
                .toList();
        return Map.of("audio", detail.audio(), "images", detail.images(), "scenes", list);
    }

    @PostMapping("/app/stories/{id}/images")
    String requestImages(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        images.requestImages(user.id(), id);
        return "redirect:/app/stories/" + id;
    }

    @PostMapping("/app/stories/{id}/audio")
    String requestAudio(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        audio.requestAudio(user.id(), id);
        return "redirect:/app/stories/" + id;
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
