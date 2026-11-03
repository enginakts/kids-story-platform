package com.kidstory.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Mobil istemci için kısa ömürlü erişim token'ı üretir. Kimlik token'ın subject alanındadır. */
@Service
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtEncoder encoder;

    @Value("${app.jwt.expiration-seconds}")
    private long expirationSeconds;

    public String issue(Long parentId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(parentId.toString())
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }
}
