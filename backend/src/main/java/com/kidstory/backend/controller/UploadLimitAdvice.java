package com.kidstory.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Yükleme sınırı aşılınca (spring.servlet.multipart) istek denetleyiciye hiç ulaşmaz. Teknik hata sayfası yerine
 * aynı sayfaya Türkçe bir uyarıyla dönülür. Yalnızca yönetim panelinde dosya yüklenir.
 */
@ControllerAdvice
public class UploadLimitAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("problem", "Yüklenen resimler çok büyük. Her resim en fazla 5 MB, hepsi birlikte en fazla 20 MB olabilir.");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "redirect:" + (path.startsWith("/admin/") ? path : "/admin");
    }
}
