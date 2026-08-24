package com.honeymesh.incident.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Wraps the jjwt library so the rest of the app never has to think about
 * token bytes directly: give it a username + roles, get back a signed
 * token; give it a token, get back the claims inside it (or an exception
 * if it's invalid, expired, or tampered with).
 *
 * jjwt 0.12.x note (this matters if you've seen older JWT tutorials):
 * the API changed from the old Jwts.builder().setSubject(...) /
 * Jwts.parser().setSigningKey(...) style to the fluent
 * .subject(...).signWith(key) / Jwts.parser().verifyWith(key).build()
 * style used below. Both exist in some versions, but 0.12.6 (what our
 * pom.xml pins) wants the new one.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms:28800000}") long expirationMs) {
        // HS256 requires a key of at least 256 bits (32 bytes). The
        // secret string in application.yml is well over that length on
        // purpose — don't shorten it, or startup will fail with a
        // WeakKeyException.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * Throws a JwtException subclass (unchecked) if the token is
     * invalid, expired, or tampered with — JwtAuthFilter is what catches
     * that, not this method.
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
