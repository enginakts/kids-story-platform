package com.kidstory.backend.controller;

import com.kidstory.backend.dto.AuthResponse;
import com.kidstory.backend.dto.ForgotPasswordRequest;
import com.kidstory.backend.dto.ParentLoginRequest;
import com.kidstory.backend.dto.ParentRegisterRequest;
import com.kidstory.backend.dto.RefreshTokenRequest;
import com.kidstory.backend.dto.ResetPasswordRequest;
import com.kidstory.backend.dto.VerifyEmailRequest;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.security.AppUser;
import com.kidstory.backend.security.JwtTokenProvider;
import com.kidstory.backend.service.AccountService;
import com.kidstory.backend.service.AuthService;
import com.kidstory.backend.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Mobil istemcinin kimlik uçları. Tümü token gerektirmez. */
@ApiController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountService accounts;
    private final RefreshTokenService refreshTokens;
    private final JwtTokenProvider jwt;
    private final AuthenticationProvider parentAuth;

    AuthController(AuthService authService, AccountService accounts, RefreshTokenService refreshTokens,
                   JwtTokenProvider jwt, @Qualifier("parentAuthProvider") AuthenticationProvider parentAuth) {
        this.authService = authService;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.parentAuth = parentAuth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody ParentRegisterRequest request, HttpServletRequest http) {
        Parent parent = authService.register(request, http.getRemoteAddr());
        return response(parent.getId(), Role.PARENT, refreshTokens.issue(parent.getId()));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody ParentLoginRequest request) {
        var result = parentAuth.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        AppUser user = (AppUser) result.getPrincipal();
        return response(user.id(), user.role(), refreshTokens.issue(user.id()));
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(request.refreshToken());
        return response(rotation.parentId(), rotation.role(), rotation.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokens.revoke(request.refreshToken());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accounts.verifyEmail(request.token());
    }

    /** Kayıtlı olsun olmasın aynı yanıt döner. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accounts.requestPasswordReset(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accounts.resetPassword(request.token(), request.newPassword());
    }

    private AuthResponse response(Long id, String role, String refreshToken) {
        return new AuthResponse(jwt.issue(id, role), "Bearer", jwt.expirationSeconds(), refreshToken);
    }
}
