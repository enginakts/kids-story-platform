package com.kidstory.backend.config;

import com.kidstory.backend.controller.AdminPageController;
import com.kidstory.backend.controller.AuthPageController;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Yalnızca geliştirme ortamında giriş sayfalarına hızlı giriş hesaplarını verir.
 * Üretimde bu sınıf yüklenmez; model boş kalır ve şablon hızlı giriş kutusunu çizmez.
 */
@Profile("dev")
@ControllerAdvice(assignableTypes = {AuthPageController.class, AdminPageController.class})
@RequiredArgsConstructor
class QuickLoginAdvice {

    private final QuickLoginProperties accounts;

    @ModelAttribute
    void quickLogin(Model model) {
        model.addAttribute("quickParents", accounts.parents());
        model.addAttribute("quickAdmins", accounts.admins());
    }
}
