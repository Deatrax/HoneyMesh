package com.honeymesh.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes are defined in Java (not YAML) deliberately: the RouteLocatorBuilder DSL
 * has been stable across Spring Cloud Gateway versions, so it's less exposed to
 * the recent config-property renames in the 2025.0.0 release train.
 *
 * In Docker Compose, service names (decoy-service, threat-engine-service,
 * incident-service) resolve via Docker's embedded DNS -> that's why the URIs
 * below use plain http://<service-name>:<port> instead of localhost.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("decoy-service", r -> r
                        .path("/api/decoy/**")
                        .uri("http://decoy-service:8081"))
                .route("threat-engine-service", r -> r
                        .path("/api/threat/**")
                        .uri("http://threat-engine-service:8082"))
                .route("incident-service", r -> r
                        .path("/api/incidents/**")
                        .uri("http://incident-service:8083"))
                .route("incident-service-auth", r -> r         
                        .path("/api/auth/**")                   
                        .uri("http://incident-service:8083")) 
                .route("incident-service-ws", r -> r
                        .path("/ws/**")
                        .uri("ws://incident-service:8083"))
                // The dummy target site — specific, known pages only, so
                // this can't accidentally swallow anything. Runs as a
                // plain host process (not in Docker), reached via Docker
                // Desktop's host.docker.internal. Must come BEFORE the
                // catch-all below: route order = match priority.
                .route("dummy-website", r -> r
                        .path("/", "/about", "/services", "/contact")
                        .uri("http://host.docker.internal:3001"))
                // Catch-all: honeypot endpoints (/api/admin/db-backup, etc.)
                // are dynamically configured, not fixed paths — anything not
                // claimed by a route above falls through here to
                // decoy-service's HoneypotController, which checks whether
                // the path matches a configured Decoy. MUST stay last: route
                // order = match priority, first match wins.
                .route("decoy-honeypot-catchall", r -> r
                        .path("/**")
                        .uri("http://decoy-service:8081"))
                .build();
    }
}
