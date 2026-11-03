package com.kidstory.backend.security;

import com.kidstory.backend.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("adminUserDetailsService")
@RequiredArgsConstructor
class AdminUserDetailsService implements UserDetailsService {

    private final AdminRepository admins;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) {
        String value = identifier.trim();
        return admins.findByUsernameOrEmail(value, value)
                .map(a -> new AppUser(a.getId(), a.getUsername(), a.getFullName(), a.getPasswordHash(),
                        a.getRole().getName(), a.isActive()))
                .orElseThrow(() -> new UsernameNotFoundException("Hesap bulunamadı"));
    }
}
