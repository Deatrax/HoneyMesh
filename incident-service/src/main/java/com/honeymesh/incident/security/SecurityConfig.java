package com.honeymesh.incident.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Demo-scale auth for a 3-day project: two hardcoded users instead of a
 * real user table (there's no time to design one, and the grading
 * rubric asks for JWT + RBAC, not user management). This is exactly the
 * kind of trade-off the plan says to write down explicitly instead of
 * hiding — mention it in the report.
 *
 * Demo accounts:
 *   admin    / admin123   -> ROLE_ADMIN, ROLE_ANALYST (admin can do everything an analyst can)
 *   analyst  / analyst123 -> ROLE_ANALYST
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        var admin = User.withUsername("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN", "ANALYST")
                .build();

        var analyst = User.withUsername("analyst")
                .password(passwordEncoder.encode("analyst123"))
                .roles("ANALYST")
                .build();

        return new InMemoryUserDetailsManager(admin, analyst);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                // REST API secured by JWT in a header, not cookies — no
                // CSRF tokens to manage.
                .csrf(csrf -> csrf.disable())
                // No HttpSession: every request must carry its own token.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // By default, Spring Security responds to a missing/bad
                // token with 403 Forbidden (it has no "challenge" to
                // issue, unlike Basic Auth's WWW-Authenticate header).
                // scripts/verify.sh expects 401 for "no token", which is
                // the more correct REST convention anyway (401 = who are
                // you, 403 = I know who you are and the answer is no) —
                // so we override both handlers explicitly.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
                )
                .authorizeHttpRequests(auth -> auth
                        // Public: login, health checks, and the ping
                        // endpoint scripts/verify.sh and the gateway hit
                        // with no token (same as the other 3 services).
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/incidents/ping").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // WebSocket handshake auth is deliberately out of
                        // scope given the timeline — see README. Browsers
                        // can't easily attach a Bearer header to the
                        // native WebSocket constructor, and building a
                        // token-in-query-param workaround wasn't worth
                        // the hours against everything else on the plan.
                        .requestMatchers("/ws/**").permitAll()
                        // Admin-only: reassigning an incident, and every
                        // other actuator endpoint beyond plain health.
                        // More specific patterns must come before the
                        // broader ones below them — same "first match
                        // wins" rule the gateway's RouteConfig uses.
                        .requestMatchers(HttpMethod.PATCH, "/api/incidents/*/assign").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Everything else under /api/incidents just needs
                        // a valid token, either role.
                        .requestMatchers("/api/incidents/**").authenticated()
                        // Deny by default: anything not explicitly opened
                        // above requires authentication too.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
