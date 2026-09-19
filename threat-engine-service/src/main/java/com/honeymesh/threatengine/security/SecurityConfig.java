package com.honeymesh.threatengine.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Same validate-only pattern as decoy-service's SecurityConfig — this
 * service issues no tokens, it only checks ones incident-service already
 * issued (see JwtService's javadoc). Unlike decoy-service, there's no
 * public honeypot surface to carve out here: ThreatController is a pure
 * internal query API (last assessment, recent assessments, correlation
 * snapshot, block status) with no create/delete operations, so the whole
 * thing can simply require a valid token — no admin-vs-analyst role split
 * needed since nothing here is destructive.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Same 401-vs-403 override incident-service and
                // decoy-service both use — 401 for missing/bad
                // credentials, 403 for "I know who you are, and no."
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
                )
                .authorizeHttpRequests(auth -> auth
                        // Public: liveness/health, same as the other 3 services —
                        // scripts/verify.sh checks this with no token, same as
                        // the others.
                        .requestMatchers("/api/threat/ping").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Everything else under /api/threat just needs a valid
                        // token, either role — same "authenticated(), no role
                        // split" treatment incident-service gives its own
                        // general (non-admin-only) endpoints.
                        .requestMatchers("/api/threat/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
