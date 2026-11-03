package com.kidstory.backend.controller;

import com.kidstory.backend.dto.ChildProfileResponse;
import com.kidstory.backend.dto.CreateChildRequest;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.ChildProfileService;
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

@Controller
@RequestMapping("/app/children")
@RequiredArgsConstructor
public class ChildPageController {

    private final ChildProfileService service;

    @GetMapping
    String list(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("form", new CreateChildRequest("", 4, null, null, null, null, null));
        return page(user, model);
    }

    @PostMapping
    String create(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") CreateChildRequest form,
                  BindingResult result, Model model) {
        if (result.hasErrors()) {
            return page(user, model);
        }
        service.create(user.id(), form);
        return "redirect:/app/children";
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
        model.addAttribute("children", service.list(user.id()));
        return "app/children";
    }

    private String edit(AppUser user, Long id, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("childId", id);
        return "app/child-edit";
    }
}
