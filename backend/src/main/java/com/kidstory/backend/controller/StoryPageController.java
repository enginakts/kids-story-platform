package com.kidstory.backend.controller;

import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.StoryRepository;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class StoryPageController {

    private final StoryRepository stories;
    private final BillingService billing;
    private final ParentRepository parents;

    @GetMapping("/app")
    String dashboard(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("emailVerified", parents.findById(user.id()).map(Parent::isEmailVerified).orElse(false));
        model.addAttribute("wallet", billing.wallet(user.id()).orElse(null));
        model.addAttribute("stories", stories.findByParentIdOrderByCreatedAtDesc(user.id()));
        return "app/dashboard";
    }

    @GetMapping("/explore")
    String explore(Model model) {
        model.addAttribute("stories", stories.findTop50ByPublishedAtNotNullOrderByPublishedAtDesc());
        return "explore";
    }
}
