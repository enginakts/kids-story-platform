package com.kidstory.backend.controller;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.CreateStoryRequest;
import com.kidstory.backend.dto.StoryViews;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.StoryRepository;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.BillingService;
import com.kidstory.backend.service.ChildProfileService;
import com.kidstory.backend.service.story.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class StoryPageController {

    private final StoryService stories;
    private final StoryRepository storyRepository;
    private final ChildProfileService children;
    private final BillingService billing;
    private final ParentRepository parents;
    private final com.kidstory.backend.service.AccountService accounts;

    /** Her çocuğun rafı: çocuk ve ona üretilen masallar. */
    public record Shelf(ChildProfileResponse child, List<StoryViews.Summary> stories) {
    }

    @GetMapping("/app")
    String dashboard(@AuthenticationPrincipal AppUser user, Model model) {
        Map<Long, List<StoryViews.Summary>> byChild = stories.listForParent(user.id()).stream()
                .collect(Collectors.groupingBy(StoryViews.Summary::childId));
        List<Shelf> shelves = children.list(user.id()).stream()
                .map(c -> new Shelf(c, byChild.getOrDefault(c.id(), List.of()).stream().limit(4).toList()))
                .toList();
        common(user, model);
        model.addAttribute("emailVerified", !accounts.isVerificationEnabled()
                || parents.findById(user.id()).map(Parent::isEmailVerified).orElse(false));
        model.addAttribute("shelves", shelves);
        return "app/dashboard";
    }

    /** Çocuğun kendi kütüphanesi: yalnızca o çocuk için üretilen masallar. */
    @GetMapping("/app/children/{childId}")
    String childLibrary(@AuthenticationPrincipal AppUser user, @PathVariable Long childId, Model model) {
        common(user, model);
        model.addAttribute("child", children.get(user.id(), childId));
        model.addAttribute("stories", stories.listForChild(user.id(), childId));
        return "app/child-library";
    }

    @GetMapping("/app/stories/new")
    String wizard(@AuthenticationPrincipal AppUser user, @RequestParam(required = false) Long childId, Model model) {
        model.addAttribute("form", CreateStoryRequest.empty(childId));
        return wizardPage(user, childId, model);
    }

    @PostMapping("/app/stories")
    String create(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") CreateStoryRequest form,
                  BindingResult result, Model model) {
        if (result.hasErrors()) {
            return wizardPage(user, form.childId(), model);
        }
        try {
            StoryViews.Detail story = stories.create(user.id(), form);
            return "redirect:/app/stories/" + story.id();
        } catch (ConflictException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            return wizardPage(user, form.childId(), model);
        }
    }

    @GetMapping("/app/stories/{id}")
    String story(@AuthenticationPrincipal AppUser user, @PathVariable Long id, Model model) {
        common(user, model);
        model.addAttribute("story", stories.get(user.id(), id));
        return "app/story";
    }

    @GetMapping("/app/stories/{id}/cover")
    ResponseEntity<Resource> cover(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return MediaResponses.of(stories.media(user.id(), id, 0, StoryService.Media.COVER));
    }

    @GetMapping("/app/stories/{id}/scenes/{number}/image")
    ResponseEntity<Resource> image(@AuthenticationPrincipal AppUser user, @PathVariable Long id, @PathVariable int number) {
        return MediaResponses.of(stories.media(user.id(), id, number, StoryService.Media.IMAGE));
    }

    @GetMapping("/app/stories/{id}/scenes/{number}/audio")
    ResponseEntity<Resource> audio(@AuthenticationPrincipal AppUser user, @PathVariable Long id, @PathVariable int number) {
        return MediaResponses.of(stories.media(user.id(), id, number, StoryService.Media.AUDIO));
    }

    @GetMapping("/explore")
    String explore(Model model) {
        model.addAttribute("stories", storyRepository.findTop50ByPublishedAtNotNullOrderByPublishedAtDesc());
        return "explore";
    }

    private String wizardPage(AppUser user, Long childId, Model model) {
        List<ChildProfileResponse> list = children.list(user.id());
        if (list.isEmpty()) {
            return "redirect:/app/children?needChild";
        }
        Long selected = childId != null ? childId : list.getFirst().id();
        common(user, model);
        model.addAttribute("children", list);
        model.addAttribute("childId", selected);
        model.addAttribute("options", stories.options(user.id(), selected));
        return "app/story-new";
    }

    private void common(AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("wallet", billing.wallet(user.id()).orElse(null));
    }
}
