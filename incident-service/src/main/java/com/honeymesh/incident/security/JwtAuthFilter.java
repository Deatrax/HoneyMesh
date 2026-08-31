package com.honeymesh.incident.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs once per request, before Spring Security's normal auth checks.
 * Reads "Authorization: Bearer <token>"; if it's a valid JWT, tells
 * Spring Security "this request is authenticated as this user, with
 * these roles" for the rest of the request.
 *
 * If there's no token, or it's invalid/expired, this filter just does
 * nothing and lets the request continue unauthenticated — it's
 * SecurityConfig's authorizeHttpRequests() rules, further down the
 * chain, that actually reject unauthenticated requests to protected
 * paths (and our custom AuthenticationEntryPoint that turns that
 * rejection into a 401, not Spring Security's 403 default).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String username = claims.getSubject();

                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);

                List<GrantedAuthority> authorities = roles.stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException invalidToken) {
                // Bad/expired/tampered token: clear anything that might
                // already be set and leave the request unauthenticated.
                // authorizeHttpRequests() decides what happens next.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
