package com.kidstory.backend.controller;

import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.service.ProfileService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Ebeveyn profili: hesap bilgileri, parola, KVKK veri indirme ve hesabı silme. */
@Controller
@RequestMapping("/app/profile")
@RequiredArgsConstructor
public class ProfilePageController {

    private final ProfileService profile;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    @GetMapping
    String page(@AuthenticationPrincipal AppUser user, Model model) {
        ProfileService.Overview o = profile.overview(user.id());
        model.addAttribute("user", user);
        model.addAttribute("profile", o);
        model.addAttribute("memberSince", DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
                .format(LocalDate.ofInstant(o.memberSince(), ZoneId.of("Europe/Istanbul"))));
        return "app/profile";
    }

    @PostMapping("/name")
    String updateName(@AuthenticationPrincipal AppUser user, @RequestParam(required = false) String firstName,
                      @RequestParam(required = false) String lastName, HttpServletRequest request, HttpServletResponse response,
                      RedirectAttributes redirect) {
        try {
            profile.updateName(user.id(), firstName, lastName);
            refreshDisplayName(user, firstName.strip(), request, response);
            redirect.addFlashAttribute("notice", "Ad ve soyad kaydedildi.");
        } catch (FieldValidationException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/app/profile#hesap";
    }

    @PostMapping("/password")
    String changePassword(@AuthenticationPrincipal AppUser user, @RequestParam(required = false) String currentPassword,
                          @RequestParam(required = false) String newPassword, RedirectAttributes redirect) {
        try {
            profile.changePassword(user.id(), currentPassword, newPassword);
            redirect.addFlashAttribute("notice", "Parolanız değiştirildi. Mobil uygulamada yeniden giriş yapmanız gerekebilir.");
        } catch (FieldValidationException e) {
            redirect.addFlashAttribute("problem", e.getMessage());
        }
        return "redirect:/app/profile#parola";
    }

    /** KVKK: verileri JSON dosyası olarak indirir (görseller ve sesler dahil değil). */
    @GetMapping("/export")
    ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AppUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"masal-dunyasi-verilerim.json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(profile.export(user.id()));
    }

    /** Hesabı kalıcı olarak siler; başarılıysa oturum kapanır ve ana sayfaya yönlendirilir. */
    @PostMapping("/delete")
    String delete(@AuthenticationPrincipal AppUser user, @RequestParam(required = false) String confirmWord,
                  @RequestParam(required = false) String password, HttpServletRequest request,
                  RedirectAttributes redirect) throws ServletException {
        try {
            profile.deleteAccount(user.id(), confirmWord, password);
        } catch (FieldValidationException e) {
            redirect.addFlashAttribute("deleteProblem", e.getMessage());
            return "redirect:/app/profile#sil";
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("deleteProblem", "Hesap şu anda silinemedi; hiçbir veriniz silinmedi. Lütfen biraz sonra tekrar deneyin.");
            return "redirect:/app/profile#sil";
        }
        request.logout();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return "redirect:/?hesapSilindi";
    }

    /** Menüde görünen ad oturumdaki kullanıcıdan gelir; yeniden giriş gerekmesin diye oturum güncellenir. */
    private void refreshDisplayName(AppUser user, String firstName, HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        AppUser updated = new AppUser(user.id(), user.login(), firstName, user.passwordHash(), user.role(), user.enabled());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null,
                current == null ? updated.getAuthorities() : current.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
