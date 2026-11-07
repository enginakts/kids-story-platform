package com.kidstory.backend.controller;

import com.kidstory.backend.dto.CharacterOptionsResponse;
import com.kidstory.backend.dto.CharacterRequest;
import com.kidstory.backend.dto.CharacterResponse;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.CharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app/characters")
@RequiredArgsConstructor
public class CharacterPageController {

    private final CharacterService service;

    @GetMapping
    String list(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("form", new CharacterRequest("", "", "", "YOK", "YOK", ""));
        return page(user, model);
    }

    @PostMapping
    String create(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") CharacterRequest form,
                  BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                CharacterResponse created = service.create(user.id(), form);
                redirect.addFlashAttribute("notice", created.name() + " karakterlerinize eklendi.");
                return "redirect:/app/characters";
            } catch (FieldValidationException e) {
                result.rejectValue(e.getField(), "rule", e.getMessage());
            } catch (ConflictException e) {
                model.addAttribute("formError", e.getMessage());
            }
        }
        return page(user, model);
    }

    @GetMapping("/{id}/edit")
    String editForm(@AuthenticationPrincipal AppUser user, @PathVariable Long id, Model model) {
        CharacterResponse c = service.get(user.id(), id);
        model.addAttribute("form", new CharacterRequest(c.name(), c.speciesCode(), c.colorCode(), c.outfitCode(),
                c.accessoryCode(), c.personalityTraits()));
        return edit(user, id, model);
    }

    @PostMapping("/{id}")
    String update(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                  @Valid @ModelAttribute("form") CharacterRequest form, BindingResult result, Model model,
                  RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                service.update(user.id(), id, form);
                redirect.addFlashAttribute("notice", "Karakter güncellendi.");
                return "redirect:/app/characters";
            } catch (FieldValidationException e) {
                result.rejectValue(e.getField(), "rule", e.getMessage());
            }
        }
        return edit(user, id, model);
    }

    @PostMapping("/{id}/delete")
    String delete(@AuthenticationPrincipal AppUser user, @PathVariable Long id, RedirectAttributes redirect) {
        service.deactivate(user.id(), id);
        redirect.addFlashAttribute("notice", "Karakter kaldırıldı.");
        return "redirect:/app/characters";
    }

    private String page(AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("own", service.own(user.id()));
        model.addAttribute("presets", service.presets());
        model.addAttribute("locations", service.locations());
        model.addAttribute("options", CharacterOptionsResponse.create());
        return "app/characters";
    }

    private String edit(AppUser user, Long id, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("characterId", id);
        model.addAttribute("options", CharacterOptionsResponse.create());
        return "app/character-edit";
    }
}
