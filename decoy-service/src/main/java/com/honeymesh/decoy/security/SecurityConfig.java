package com.honeymesh.decoy.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Unlike incident-service, this service issues no tokens and defines no
 * users — it only validates tokens incident-service already issued (see
 * JwtService). The one thing that makes this SecurityConfig different
 * from a plain "lock everything down" setup: the honeypot catch-all
 * (HoneypotController's "/**") MUST stay public. Requiring auth there
 * would defeat the entire point of a honeypot — a real attacker hitting a
 * fake admin panel obviously doesn't have a valid analyst token — and the
 * blocklist-enforcement check inside HoneypotController.handleHit() needs
 * to run unconditionally, before any auth decision, or a blocked IP would
 * never even reach the code that rejects it with a 403.
 *
 * So the split is: DecoyAdminController's real admin surface
 * (/api/decoy/admin/**) — create/enable/disable/delete/hit-detail — now
 * requires a token; everything else, the ping, actuator health, and above
 * all the honeypot catch-all, stays exactly as open as it was before.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                // REST API secured by JWT in a header, not cookies — no
                // CSRF tokens to manage. Same as incident-service.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Same 401-vs-403 override incident-service uses: Spring
                // Security's default for a missing/bad token is 403, but
                // 401 (who are you) is the more correct code for "no/bad
                // credentials" — 403 should mean "I know who you are, and
                // no." verify.sh checks for exactly this distinction.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
                )
                .authorizeHttpRequests(auth -> auth
                        // Public: liveness/health, same as the other services.
                        .requestMatchers("/api/decoy/ping").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Admin-only mutations on decoys — same rationale as
                        // incident-service restricting assign/unblock/perma-block
                        // to ROLE_ADMIN: creating, enabling, disabling, or
                        // deleting a decoy changes what the whole system does,
                        // not just what one analyst sees. More specific patterns
                        // must come before the broader ones below them — first
                        // match wins, the same rule the gateway's RouteConfig
                        // and incident-service's SecurityConfig both follow.
                        .requestMatchers(HttpMethod.POST, "/api/decoy/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/decoy/admin/*/enable").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/decoy/admin/*/disable").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/decoy/admin/**").hasRole("ADMIN")
                        // Read-only admin views (list, get-by-id, forensics
                        // lookup) — any logged-in analyst, not just admins,
                        // same split incident-service uses between its
                        // admin-only actions and its general "authenticated()"
                        // reads.
                        .requestMatchers(HttpMethod.GET, "/api/decoy/admin/**").authenticated()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Everything else — the honeypot catch-all included —
                        // stays public. Deliberate, not a leftover: see the
                        // class-level javadoc above.
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
