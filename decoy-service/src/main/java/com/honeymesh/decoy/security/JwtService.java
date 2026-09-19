package com.honeymesh.decoy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validate-only counterpart to incident-service's JwtService. This
 * service never issues a token — there's no login here, analysts only
 * ever authenticate against incident-service's /api/auth/login — it just
 * needs to check that a token handed to it was signed with the same
 * shared secret and hasn't expired. Same jjwt 0.12.x fluent API
 * incident-service uses (verifyWith(key), not the older setSigningKey
 * style).
 */
@Component
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        // Same >= 256-bit requirement as incident-service's JwtService —
        // this is why application.yml's default secret string is long.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Throws a JwtException subclass (unchecked) if the token is invalid,
     * expired, or tampered with — JwtAuthFilter is what catches that, not
     * this method.
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
