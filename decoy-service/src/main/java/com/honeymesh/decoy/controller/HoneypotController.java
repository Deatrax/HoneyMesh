package com.honeymesh.decoy.controller;

import com.honeymesh.decoy.config.RabbitConfig;
import com.honeymesh.decoy.dto.RequestForensics;
import com.honeymesh.decoy.entity.Decoy;
import com.honeymesh.decoy.event.TelemetryEvent;
import com.honeymesh.decoy.service.DecoyService;
import com.honeymesh.decoy.service.RequestForensicsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
public class HoneypotController {

    private static final Set<String> RESERVED_PREFIXES =
            Set.of("/api/decoy/admin", "/api/decoy/ping", "/actuator", "/error");

    private static final String BLOCK_KEY_PREFIX = "honeymesh:block:";

    private final DecoyService decoyService;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RequestForensicsService forensicsService;

    public HoneypotController(DecoyService decoyService, RabbitTemplate rabbitTemplate,
                               StringRedisTemplate redisTemplate, RequestForensicsService forensicsService) {
        this.decoyService = decoyService;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.forensicsService = forensicsService;
    }

    @RequestMapping("/**")
    public ResponseEntity<Map<String, String>> handleHit(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (RESERVED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return ResponseEntity.notFound().build();
        }

        String clientIp = resolveClientIp(request);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLOCK_KEY_PREFIX + clientIp))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "blocked", "reason", "This source is temporarily blocked."));
        }

        return decoyService.findByEndpointPath(path)
                .filter(Decoy::isEnabled)
                .map(decoy -> {
                    publishHit(decoy, request);
                    captureForensics(request, clientIp);
                    return ResponseEntity.ok(Map.of("status", "ok"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

        private void publishHit(Decoy decoy, HttpServletRequest request) {
        TelemetryEvent event = new TelemetryEvent(
                String.valueOf(decoy.getId()),
                resolveClientIp(request),
                decoy.getEndpointPath(),
                decoy.getRiskLevel(),
                Instant.now()
        );
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.TELEMETRY_ROUTING_KEY, event);
    }

    private void captureForensics(HttpServletRequest request, String clientIp) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name, request.getHeader(name)));

        String queryString = request.getQueryString();
        String uri = request.getRequestURI() + (queryString != null ? "?" + queryString : "");

        forensicsService.save(new RequestForensics(clientIp, request.getMethod(), uri, headers, Instant.now()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
