package com.kidstory.backend.controller;

import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.StoryRepository;
import com.kidstory.backend.security.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPageController {

    private final ParentRepository parents;
    private final StoryRepository stories;

    @GetMapping("/login")
    String login() {
        return "admin/login";
    }

    @GetMapping
    String dashboard(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("parentCount", parents.count());
        model.addAttribute("storyCount", stories.count());
        model.addAttribute("pendingCount", stories.countByModerationStatus(StoryEnums.ModerationStatus.PENDING));
        return "admin/dashboard";
    }
}
