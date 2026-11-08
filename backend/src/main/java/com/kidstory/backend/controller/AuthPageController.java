package com.kidstory.backend.controller;

import com.kidstory.backend.dto.ForgotPasswordRequest;
import com.kidstory.backend.dto.ParentRegisterRequest;
import com.kidstory.backend.dto.ResetPasswordRequest;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.EmailDeliveryException;
import com.kidstory.backend.exception.InvalidTokenException;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.AccountService;
import com.kidstory.backend.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthPageController {

    private final AuthService authService;
    private final AccountService accounts;

    @GetMapping("/")
    String home() {
        return "index";
    }

    /** Giriş ve kayıt aynı kitabın iki sayfasıdır; ikisi de auth/entry şablonunu açar, "mode" açık sayfayı seçer. */
    @GetMapping("/login")
    String login(Model model) {
        return entry(model, "login", new ParentRegisterRequest("", "", "", "", false));
    }

    @GetMapping("/register")
    String registerForm(Model model) {
        return entry(model, "register", new ParentRegisterRequest("", "", "", "", false));
    }

    private static String entry(Model model, String mode, ParentRegisterRequest form) {
        model.addAttribute("mode", mode);
        model.addAttribute("plateFolio", "register".equals(mode) ? "14" : "12");
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", form);
        }
        return "auth/entry";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("form") ParentRegisterRequest form, BindingResult result,
                    HttpServletRequest http, Model model) throws ServletException {
        if (result.hasErrors()) {
            return entry(model, "register", form);
        }
        try {
            authService.register(form, http.getRemoteAddr());
        } catch (ConflictException e) {
            result.rejectValue("email", "duplicate", e.getMessage());
            return entry(model, "register", form);
        }
        http.login(form.email().trim(), form.password());
        return "redirect:/app";
    }

    @GetMapping("/verify-email")
    String verifyEmail(@RequestParam(required = false) String token, Model model) {
        try {
            accounts.verifyEmail(token);
            model.addAttribute("success", true);
        } catch (InvalidTokenException e) {
            model.addAttribute("success", false);
        }
        return "auth/verify-result";
    }

    @PostMapping("/app/verify-email/resend")
    String resendVerification(@AuthenticationPrincipal AppUser user) {
        try {
            accounts.sendVerification(user.id());
        } catch (EmailDeliveryException e) {
            return "redirect:/app?verificationFailed";
        }
        return "redirect:/app?verificationSent";
    }

    @GetMapping("/forgot-password")
    String forgotForm(Model model) {
        model.addAttribute("form", new ForgotPasswordRequest(""));
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    String forgot(@Valid @ModelAttribute("form") ForgotPasswordRequest form, BindingResult result, Model model) {
        if (!result.hasErrors()) {
            accounts.requestPasswordReset(form.email());
            model.addAttribute("sent", true);
        }
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("form", new ResetPasswordRequest(token, ""));
        model.addAttribute("usable", accounts.isResetTokenUsable(token));
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    String reset(@Valid @ModelAttribute("form") ResetPasswordRequest form, BindingResult result, Model model) {
        model.addAttribute("usable", true);
        if (result.hasErrors()) {
            return "auth/reset-password";
        }
        try {
            accounts.resetPassword(form.token(), form.newPassword());
        } catch (InvalidTokenException e) {
            model.addAttribute("usable", false);
            return "auth/reset-password";
        }
        return "redirect:/login?reset";
    }
}
