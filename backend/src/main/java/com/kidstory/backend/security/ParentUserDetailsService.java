package com.kidstory.backend.security;

import com.kidstory.backend.repository.ParentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service("parentUserDetailsService")
@RequiredArgsConstructor
class ParentUserDetailsService implements UserDetailsService {

    private final ParentRepository parents;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return parents.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(p -> new AppUser(p.getId(), p.getEmail(), p.getFirstName(), p.getPasswordHash(),
                        p.getRole().getName(), p.isActive()))
                .orElseThrow(() -> new UsernameNotFoundException("Hesap bulunamadı"));
    }
}
