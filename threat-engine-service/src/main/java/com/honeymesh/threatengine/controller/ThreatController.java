package com.honeymesh.threatengine.controller;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import com.honeymesh.threatengine.listener.TelemetryListener;
import com.honeymesh.threatengine.model.BlockStatusResponse;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.SourceCorrelationResponse;
import com.honeymesh.threatengine.service.BlocklistService;
import com.honeymesh.threatengine.service.CorrelationService;
import com.honeymesh.threatengine.service.ThreatAssessmentHistoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/threat")
public class ThreatController {

    private final TelemetryListener telemetryListener;
    private final CorrelationService correlationService;
    private final BlocklistService blocklistService;
    private final ThreatAssessmentHistoryService historyService;
    private final StringRedisTemplate redisTemplate;

    public ThreatController(TelemetryListener telemetryListener,
                            CorrelationService correlationService,
                            BlocklistService blocklistService,
                            ThreatAssessmentHistoryService historyService,
                            StringRedisTemplate redisTemplate) {
        this.telemetryListener = telemetryListener;
        this.correlationService = correlationService;
        this.blocklistService = blocklistService;
        this.historyService = historyService;
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

    @GetMapping("/last-assessment")
    public ResponseEntity<ThreatAssessmentEvent> lastAssessment() {
        ThreatAssessmentEvent latest = historyService.getLatestAssessment();
        if (latest == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/last-assessment-event")
    public ResponseEntity<ThreatAssessmentEvent> lastAssessmentEvent() {
        return lastAssessment();
    }

    @GetMapping("/recent-assessments")
    public List<ThreatAssessmentEvent> recentAssessments() {
        return historyService.getRecentAssessments();
    }

    @GetMapping("/source/{sourceIp}")
    public SourceCorrelationResponse sourceCorrelation(@PathVariable String sourceIp) {
        CorrelationSnapshot snapshot = correlationService.getCorrelationSnapshot(sourceIp);
        boolean blocked = blocklistService.isBlocked(sourceIp);
        return new SourceCorrelationResponse(
                sourceIp,
                snapshot.recentHitCount(),
                snapshot.distinctDecoyCount(),
                snapshot.highestRiskLevel(),
                blocked
        );
    }

    @GetMapping("/block/{sourceIp}")
    public BlockStatusResponse blockStatus(@PathVariable String sourceIp) {
        boolean blocked = blocklistService.isBlocked(sourceIp);
        long ttlSeconds = blocklistService.getRemainingTtlSeconds(sourceIp);
        return new BlockStatusResponse(sourceIp, blocked, ttlSeconds);
    }

    @GetMapping("/blocklist/{sourceIp}")
    public BlockStatusResponse blocklistStatus(@PathVariable String sourceIp) {
        return blockStatus(sourceIp);
    }
}
