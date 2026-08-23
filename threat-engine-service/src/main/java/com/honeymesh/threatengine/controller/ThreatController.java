package com.honeymesh.threatengine.controller;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.listener.TelemetryListener;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.ThreatAssessment;
import com.honeymesh.threatengine.service.BlocklistService;
import com.honeymesh.threatengine.service.CorrelationService;
import com.honeymesh.threatengine.service.ThreatScoringService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/threat")
public class ThreatController {

    private final TelemetryListener telemetryListener;
    private final CorrelationService correlationService;
    private final ThreatScoringService threatScoringService;
    private final BlocklistService blocklistService;
    private final StringRedisTemplate redisTemplate;

    public ThreatController(TelemetryListener telemetryListener,
                            CorrelationService correlationService,
                            ThreatScoringService threatScoringService,
                            BlocklistService blocklistService,
                            StringRedisTemplate redisTemplate) {
        this.telemetryListener = telemetryListener;
        this.correlationService = correlationService;
        this.threatScoringService = threatScoringService;
        this.blocklistService = blocklistService;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/ping")
    public String ping() {
        return "threat-engine-service is up";
    }

    @GetMapping("/last-event")
    public TelemetryEvent lastEvent() {
        return telemetryListener.getLastEvent();
    }

    @GetMapping("/count/{decoyId}")
    public String countForDecoy(@PathVariable String decoyId) {
        String value = redisTemplate.opsForValue().get("honeymesh:telemetry:count:" + decoyId);
        return value == null ? "0" : value;
    }

    @GetMapping("/correlation/{sourceIp}")
    public CorrelationSnapshot correlationForSourceIp(@PathVariable String sourceIp) {
        return correlationService.getCorrelationSnapshot(sourceIp);
    }

    @GetMapping("/assessment/{sourceIp}")
    public ThreatAssessment assessmentForSourceIp(@PathVariable String sourceIp) {
        CorrelationSnapshot snapshot = correlationService.getCorrelationSnapshot(sourceIp);
        return threatScoringService.assess(snapshot);
    }

    @GetMapping("/last-assessment")
    public ThreatAssessment lastAssessment() {
        return telemetryListener.getLastAssessment();
    }

    @GetMapping("/last-assessment-event")
    public com.honeymesh.threatengine.event.ThreatAssessmentEvent lastAssessmentEvent() {
        return telemetryListener.getLastAssessmentEvent();
    }

    @GetMapping("/blocklist/{sourceIp}")
    public Map<String, Object> blocklistStatus(@PathVariable String sourceIp) {
        boolean blocked = blocklistService.isBlocked(sourceIp);
        long ttlSeconds = blocklistService.getRemainingTtlSeconds(sourceIp);
        return Map.of(
                "sourceIp", sourceIp,
                "blocked", blocked,
                "ttlSeconds", ttlSeconds
        );
    }
}




