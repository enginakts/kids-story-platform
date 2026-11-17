package com.kidstory.backend.controller;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.CreateChildRequest;
import com.kidstory.backend.dto.StorySummaryResponse;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.ChildProfileService;
import com.kidstory.backend.service.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/app/children")
@RequiredArgsConstructor
public class ChildPageController {

    private final ChildProfileService service;
    private final StoryService stories;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
            .withZone(ZoneId.of("Europe/Istanbul"));

    /** Çocuk sayfasındaki masal kartı: özet ve okunur tarih. */
    public record StoryCard(StorySummaryResponse story, String date) {
    }

    @GetMapping
    String list(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("form", new CreateChildRequest("", 4, null, null, null, null, null));
        return page(user, model);
    }

    @PostMapping
    String create(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") CreateChildRequest form,
                  BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("formOpen", true); // hatalı form açık gelsin
            return page(user, model);
        }
        service.create(user.id(), form);
        return "redirect:/app/children";
    }

    /** Bir çocuğun masal alanı. Çocuk bu ebeveyne ait değilse ya da kaldırılmışsa 404. */
    @GetMapping("/{id}")
    String detail(@AuthenticationPrincipal AppUser user, @PathVariable Long id, Model model) {
        ChildProfileResponse child;
        try {
            child = service.get(user.id(), id);
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        List<StoryCard> cards = stories.listForChild(user.id(), id).stream()
                .map(s -> new StoryCard(s, DATE.format(s.createdAt()))).toList();
        model.addAttribute("user", user);
        model.addAttribute("child", child);
        model.addAttribute("cards", cards);
        // "Son üretilen": bu çocuk için kaydedilmemiş, süresi dolmamış en yeni masal
        stories.latestUnsaved(user.id(), id).ifPresent(l -> model.addAttribute("latest", l));
        return "app/child";
    }

    @GetMapping("/{id}/edit")
    String editForm(@AuthenticationPrincipal AppUser user, @PathVariable Long id, Model model) {
        ChildProfileResponse c = service.get(user.id(), id);
        model.addAttribute("form", new CreateChildRequest(c.name(), c.age(), c.gender(), c.favoriteAnimals(),
                c.favoriteColors(), c.specialInterests(), c.bedtimeHour()));
        return edit(user, id, model);
    }

    @PostMapping("/{id}")
    String update(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                  @Valid @ModelAttribute("form") CreateChildRequest form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return edit(user, id, model);
        }
        service.update(user.id(), id, form);
        return "redirect:/app/children";
    }

    @PostMapping("/{id}/delete")
    String delete(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        service.deactivate(user.id(), id);
        return "redirect:/app/children";
    }

    private String page(AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("emailVerified", stories.emailVerified(user.id()));
        model.addAttribute("children", service.list(user.id()));
        stories.latestUnsaved(user.id(), null).ifPresent(l -> model.addAttribute("latest", l));
        return "app/children";
    }

    private String edit(AppUser user, Long id, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("childId", id);
        return "app/child-edit";
    }
}
