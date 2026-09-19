package com.honeymesh.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                .route("dummy-website", r -> r
                        .path("/", "/about", "/services", "/contact")
                        .uri("http://host.docker.internal:3001"))
                .route("decoy-honeypot-catchall", r -> r
                        .path("/**")
                        .uri("http://decoy-service:8081"))
                .build();
    }
}
