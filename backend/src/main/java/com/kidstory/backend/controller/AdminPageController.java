package com.kidstory.backend.controller;

import com.kidstory.backend.dto.AdminForm;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.entity.StoryModerationReview;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.AdminService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Yönetim paneli. Tüm yöneticiler genel bakış, masallar ve ebeveynler sayfalarını kullanır;
 * yapay zekâ sağlayıcıları ve yöneticiler yalnızca süper yöneticiye açıktır (SecurityConfig).
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPageController {

    private final AdminService admin;

    @ModelAttribute
    void common(@AuthenticationPrincipal AppUser user, Model model) {
        if (user != null) {
            model.addAttribute("user", user);
            model.addAttribute("superAdmin", Role.SUPER_ADMIN.equals(user.role()));
        }
    }

    @GetMapping("/login")
    String login() {
        return "admin/login";
    }

    @GetMapping
    String dashboard(Model model) {
        model.addAttribute("overview", admin.overview());
        return "admin/dashboard";
    }

    // ---------- Masallar ----------

    @GetMapping("/stories")
    String stories(@RequestParam(defaultValue = "all") String filter, Model model) {
        String f = AdminService.knownStoryFilter(filter) ? filter : "all";
        model.addAttribute("filter", f);
        model.addAttribute("stories", admin.stories(f, 100));
        return "admin/stories";
    }

    @GetMapping("/stories/{id}")
    String story(@PathVariable long id, Model model) {
        model.addAttribute("story", admin.story(id));
        model.addAttribute("scenes", admin.scenes(id));
        return "admin/story";
    }

    @PostMapping("/stories/{id}/moderate")
    String moderate(@AuthenticationPrincipal AppUser user, @PathVariable long id, @RequestParam StoryModerationReview.Decision decision,
                    @RequestParam(required = false) String reason, HttpServletRequest request, RedirectAttributes redirect) {
        admin.moderate(user.id(), id, decision, reason, request.getRemoteAddr());
        redirect.addFlashAttribute("notice", decision == StoryModerationReview.Decision.APPROVED ? "Masal onaylandı." : "Masal reddedildi.");
        return "redirect:/admin/stories/" + id;
    }

    // ---------- Ebeveynler ----------

    @GetMapping("/parents")
    String parents(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("parents", admin.parents(q));
        model.addAttribute("maxCredits", AdminService.MAX_CREDIT_GRANT);
        return "admin/parents";
    }

    @PostMapping("/parents/{id}/credits")
    String credits(@AuthenticationPrincipal AppUser user, @PathVariable long id, @RequestParam int amount,
                   HttpServletRequest request, RedirectAttributes redirect) {
        try {
            admin.grantCredits(user.id(), id, amount, request.getRemoteAddr());
            redirect.addFlashAttribute("notice", amount + " masal kredisi eklendi.");
        } catch (FieldValidationException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/parents";
    }

    @PostMapping("/parents/{id}/active")
    String parentActive(@AuthenticationPrincipal AppUser user, @PathVariable long id, @RequestParam boolean active,
                        HttpServletRequest request, RedirectAttributes redirect) {
        admin.setParentActive(user.id(), id, active, request.getRemoteAddr());
        redirect.addFlashAttribute("notice", active ? "Hesap yeniden açıldı." : "Hesap askıya alındı; bu ebeveyn giriş yapamaz.");
        return "redirect:/admin/parents";
    }

    // ---------- Yapay zekâ sağlayıcıları (süper yönetici) ----------

    @GetMapping("/ai")
    String providers(Model model) {
        model.addAttribute("providers", admin.providers());
        return "admin/ai";
    }

    @PostMapping("/ai/{id}")
    String updateProvider(@AuthenticationPrincipal AppUser user, @PathVariable long id,
                          @RequestParam(defaultValue = "false") boolean active, @RequestParam int priority,
                          HttpServletRequest request, RedirectAttributes redirect) {
        try {
            admin.updateProvider(user.id(), id, active, priority, request.getRemoteAddr());
            redirect.addFlashAttribute("notice", "Sağlayıcı güncellendi.");
        } catch (FieldValidationException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/ai";
    }

    // ---------- Yöneticiler (süper yönetici) ----------

    @GetMapping("/admins")
    String admins(Model model) {
        model.addAttribute("form", AdminForm.empty());
        return adminsPage(model);
    }

    @PostMapping("/admins")
    String createAdmin(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") AdminForm form, BindingResult result,
                       HttpServletRequest request, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                admin.createAdmin(user.id(), new AdminService.NewAdmin(form.username(), form.email(), form.fullName(), form.password(), form.role()),
                        request.getRemoteAddr());
                redirect.addFlashAttribute("notice", form.username() + " yönetici olarak eklendi.");
                return "redirect:/admin/admins";
            } catch (FieldValidationException e) {
                result.rejectValue(e.getField(), "invalid", e.getMessage());
            }
        }
        return adminsPage(model);
    }

    @PostMapping("/admins/{id}/active")
    String adminActive(@AuthenticationPrincipal AppUser user, @PathVariable long id, @RequestParam boolean active,
                       HttpServletRequest request, RedirectAttributes redirect) {
        try {
            admin.setAdminActive(user.id(), id, active, request.getRemoteAddr());
            redirect.addFlashAttribute("notice", active ? "Yönetici yeniden açıldı." : "Yönetici askıya alındı.");
        } catch (ConflictException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/admin/admins";
    }

    private String adminsPage(Model model) {
        model.addAttribute("admins", admin.admins());
        model.addAttribute("audits", admin.recentAudits(30));
        return "admin/admins";
    }
}
