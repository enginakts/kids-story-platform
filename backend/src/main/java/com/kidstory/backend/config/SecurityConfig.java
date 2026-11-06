package com.kidstory.backend.config;

import com.kidstory.backend.entity.Role;
import com.kidstory.backend.security.LoginAttemptService;
import com.kidstory.backend.security.ThrottledAuthenticationProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;

import java.util.Map;

/**
 * Üç ayrı güvenlik zinciri:
 * 1) /api/** mobil REST API, JWT ile, oturumsuz.
 * 2) /admin/** yönetim paneli, yönetici tablosuna karşı form girişi.
 * 3) Geri kalan web arayüzü, ebeveyn tablosuna karşı form girişi ve CSRF koruması.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationProvider parentAuthProvider(@Qualifier("parentUserDetailsService") UserDetailsService service,
                                              PasswordEncoder encoder, LoginAttemptService attempts) {
        return throttled(service, encoder, attempts, "parent");
    }

    @Bean
    AuthenticationProvider adminAuthProvider(@Qualifier("adminUserDetailsService") UserDetailsService service,
                                             PasswordEncoder encoder, LoginAttemptService attempts) {
        return throttled(service, encoder, attempts, "admin");
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().hasAuthority(Role.PARENT))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminChain(HttpSecurity http,
                                   @Qualifier("adminAuthProvider") AuthenticationProvider provider) throws Exception {
        return http
                .securityMatcher("/admin/**")
                .authenticationProvider(provider)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasAnyAuthority(Role.ADMIN, Role.SUPER_ADMIN))
                .formLogin(f -> f.loginPage("/admin/login").defaultSuccessUrl("/admin", true)
                        .failureHandler(failureHandler("/admin/login")).permitAll())
                .logout(l -> l.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout"))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain webChain(HttpSecurity http,
                                 @Qualifier("parentAuthProvider") AuthenticationProvider provider) throws Exception {
        return http
                .authenticationProvider(provider)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/login", "/register", "/explore", "/verify-email",
                                "/forgot-password", "/reset-password", "/css/**", "/js/**", "/img/**", "/error").permitAll()
                        .requestMatchers("/app/**").hasAuthority(Role.PARENT)
                        .anyRequest().denyAll())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/app", true)
                        .failureHandler(failureHandler("/login")).permitAll())
                .logout(Customizer.withDefaults())
                .build();
    }

    private static AuthenticationProvider throttled(UserDetailsService service, PasswordEncoder encoder,
                                                    LoginAttemptService attempts, String realm) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(service);
        provider.setPasswordEncoder(encoder);
        return new ThrottledAuthenticationProvider(provider, attempts, realm);
    }

    private static ExceptionMappingAuthenticationFailureHandler failureHandler(String loginPage) {
        ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setDefaultFailureUrl(loginPage + "?error");
        handler.setExceptionMappings(Map.of(LockedException.class.getName(), loginPage + "?locked"));
        return handler;
    }
}
