package com.kidstory.backend.controller;

import com.kidstory.backend.dto.AdminLoginRequest;
import com.kidstory.backend.dto.AuthResponse;
import com.kidstory.backend.dto.ParentLoginRequest;
import com.kidstory.backend.dto.ParentRegisterRequest;
import com.kidstory.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Kimlik Doğrulama Controller'ı
 * .NET Core Karşılığı: [ApiController] [Route("api/[controller]")] public class AuthController : ControllerBase
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Ebeveyn Girişi
     * POST /api/auth/parent/login
     */
    @PostMapping("/parent/login")
    public ResponseEntity<?> loginParent(@Valid @RequestBody ParentLoginRequest request) {
        try {
            AuthResponse response = authService.loginParent(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Giriş işlemi sırasında bir hata oluştu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Ebeveyn Kaydı
     * POST /api/auth/parent/register
     */
    @PostMapping("/parent/register")
    public ResponseEntity<?> registerParent(@Valid @RequestBody ParentRegisterRequest request) {
        try {
            AuthResponse response = authService.registerParent(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Kayıt işlemi sırasında bir hata oluştu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Yönetici Girişi
     * POST /api/auth/admin/login
     */
    @PostMapping("/admin/login")
    public ResponseEntity<?> loginAdmin(@Valid @RequestBody AdminLoginRequest request) {
        try {
            AuthResponse response = authService.loginAdmin(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Yönetici girişi sırasında bir hata oluştu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * API Durum Kontrolü
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "Kids Story Auth Service");
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }
}
