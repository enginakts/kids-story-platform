package com.kidstory.backend.controller;

import com.kidstory.backend.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Giriş yapmış ebeveynin hesap işlemleri (mobil). */
@ApiController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accounts;

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@AuthenticationPrincipal Jwt jwt) {
        accounts.sendVerification(Long.valueOf(jwt.getSubject()));
    }
}
