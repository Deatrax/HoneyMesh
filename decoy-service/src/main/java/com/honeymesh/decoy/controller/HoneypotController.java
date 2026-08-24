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

/**
 * The actual decoys. Any path NOT claimed by a real controller (admin CRUD,
 * ping, actuator) falls through to here. If that path matches an enabled
 * Decoy's configured endpointPath, we record a real telemetry hit and
 * publish it — this is what replaces the old manual /test-event trigger.
 *
 * IMPORTANT: this only works because the gateway has a catch-all route
 * sending unmatched traffic to decoy-service — see RouteConfig.java. Without
 * that, a request to e.g. /api/admin/db-backup never reaches this service at
 * all, since the gateway previously only forwarded /api/decoy/** here.
 */
@RestController
public class HoneypotController {

    // Defense in depth: Spring MVC's own routing already prefers the more
    // specific mappings in DecoyAdminController/DecoyController over this
    // class's "/**", so this shouldn't be strictly necessary — but it's
    // free insurance against this wildcard ever swallowing something it
    // shouldn't (actuator, error dispatch).
    private static final Set<String> RESERVED_PREFIXES =
            Set.of("/api/decoy/admin", "/api/decoy/ping", "/actuator", "/error");

    // Same key format threat-engine-service's BlocklistService writes
    // (honeymesh:block:<ip>). Deliberately reading the SAME Redis keys
    // instead of calling threat-engine-service's API — one shared piece
    // of fast state, no new service-to-service HTTP call, no new failure
    // mode if that service is briefly slow.
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

    // Full request detail for the analyst view — separate from the
    // TelemetryEvent published above on purpose (see RequestForensics'
    // javadoc: this stays local to decoy-service via Redis instead of
    // being threaded through two more event contracts downstream).
    private void captureForensics(HttpServletRequest request, String clientIp) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name, request.getHeader(name)));

        String queryString = request.getQueryString();
        String uri = request.getRequestURI() + (queryString != null ? "?" + queryString : "");

        forensicsService.save(new RequestForensics(clientIp, request.getMethod(), uri, headers, Instant.now()));
    }

    // Requests arrive here already proxied through the gateway, so
    // request.getRemoteAddr() would return the GATEWAY's container IP, not
    // the real caller's — every hit would show the same wrong source IP.
    // Spring Cloud Gateway adds X-Forwarded-For by default; read that first.
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
