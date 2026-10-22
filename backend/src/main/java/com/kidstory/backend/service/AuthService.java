package com.kidstory.backend.service;

import com.kidstory.backend.dto.AdminLoginRequest;
import com.kidstory.backend.dto.AuthResponse;
import com.kidstory.backend.dto.ParentLoginRequest;
import com.kidstory.backend.dto.ParentRegisterRequest;
import com.kidstory.backend.entity.Admin;
import com.kidstory.backend.entity.CreditWallet;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.Role;
import com.kidstory.backend.repository.AdminRepository;
import com.kidstory.backend.repository.CreditWalletRepository;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.RoleRepository;
import com.kidstory.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ParentRepository parentRepository;
    private final AdminRepository adminRepository;
    private final RoleRepository roleRepository;
    private final CreditWalletRepository creditWalletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Ebeveyn Girişi (.NET Core: Task<AuthResponseDto> LoginParentAsync)
     */
    @Transactional
    public AuthResponse loginParent(ParentLoginRequest request) {
        Parent parent = parentRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("E-posta adresi veya şifre hatalı"));

        if (!passwordEncoder.matches(request.getPassword(), parent.getPasswordHash())) {
            throw new IllegalArgumentException("E-posta adresi veya şifre hatalı");
        }

        if (!Boolean.TRUE.equals(parent.getIsActive())) {
            throw new IllegalStateException("Bu ebeveyn hesabı devre dışı bırakılmıştır.");
        }

        parent.setLastLoginAt(LocalDateTime.now());
        parentRepository.save(parent);

        CreditWallet wallet = creditWalletRepository.findByParentId(parent.getId()).orElse(null);

        String token = jwtTokenProvider.generateToken(
                parent.getEmail(),
                "PARENT",
                parent.getRole() != null ? parent.getRole().getName() : "ROLE_PARENT",
                parent.getId()
        );

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userType("PARENT")
                .id(parent.getId())
                .email(parent.getEmail())
                .fullName(parent.getFirstName() + " " + parent.getLastName())
                .role(parent.getRole() != null ? parent.getRole().getName() : "ROLE_PARENT")
                .storyCredits(wallet != null ? wallet.getStoryCreditBalance() : 0)
                .interactiveCredits(wallet != null ? wallet.getInteractiveCreditBalance() : 0)
                .build();
    }

    /**
     * Ebeveyn Kaydı (.NET Core: Task<AuthResponseDto> RegisterParentAsync)
     */
    @Transactional
    public AuthResponse registerParent(ParentRegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (parentRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Bu e-posta adresi ile kayıtlı bir hesap zaten mevcuttur.");
        }

        Role parentRole = roleRepository.findByName("ROLE_PARENT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_PARENT")
                        .displayName("Ebeveyn")
                        .description("Masal üreten ve çocuk profili yöneten standart ebeveyn hesabı")
                        .build()));

        Parent parent = Parent.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(request.getPhoneNumber())
                .preferredLanguage(request.getPreferredLanguage() != null ? request.getPreferredLanguage() : "tr")
                .role(parentRole)
                .isActive(true)
                .isEmailVerified(false)
                .lastLoginAt(LocalDateTime.now())
                .avatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=" + request.getFirstName().trim())
                .build();

        Parent savedParent = parentRepository.save(parent);

        // Yeni ebeveyne hoş geldin deneme kredileri (3 tek seferlik masal, 1 etkileşimli)
        CreditWallet wallet = CreditWallet.builder()
                .parent(savedParent)
                .storyCreditBalance(3)
                .interactiveCreditBalance(1)
                .build();
        creditWalletRepository.save(wallet);

        String token = jwtTokenProvider.generateToken(
                savedParent.getEmail(),
                "PARENT",
                parentRole.getName(),
                savedParent.getId()
        );

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userType("PARENT")
                .id(savedParent.getId())
                .email(savedParent.getEmail())
                .fullName(savedParent.getFirstName() + " " + savedParent.getLastName())
                .role(parentRole.getName())
                .storyCredits(wallet.getStoryCreditBalance())
                .interactiveCredits(wallet.getInteractiveCreditBalance())
                .build();
    }

    /**
     * Yönetici Girişi (.NET Core: Task<AuthResponseDto> LoginAdminAsync)
     */
    @Transactional
    public AuthResponse loginAdmin(AdminLoginRequest request) {
        String identifier = request.getUsernameOrEmail().trim();

        Admin admin = adminRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new IllegalArgumentException("Yönetici bilgileri veya şifre hatalı"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new IllegalArgumentException("Yönetici bilgileri veya şifre hatalı");
        }

        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw new IllegalStateException("Bu yönetici hesabı devre dışı bırakılmıştır.");
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);

        String token = jwtTokenProvider.generateToken(
                admin.getUsername(),
                "ADMIN",
                admin.getRole() != null ? admin.getRole().getName() : "ROLE_ADMIN",
                admin.getId()
        );

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userType("ADMIN")
                .id(admin.getId())
                .email(admin.getEmail())
                .username(admin.getUsername())
                .fullName(admin.getFullName())
                .role(admin.getRole() != null ? admin.getRole().getName() : "ROLE_ADMIN")
                .build();
    }
}
