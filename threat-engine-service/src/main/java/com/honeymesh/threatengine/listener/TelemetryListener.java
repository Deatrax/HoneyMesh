package com.honeymesh.threatengine.listener;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.ThreatAssessment;
import com.honeymesh.threatengine.model.ThreatLevel;
import com.honeymesh.threatengine.publisher.ThreatAssessmentPublisher;
import com.honeymesh.threatengine.service.BlocklistService;
import com.honeymesh.threatengine.service.CorrelationService;
import com.honeymesh.threatengine.service.ThreatScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class TelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryListener.class);

    private final StringRedisTemplate redisTemplate;
    private final CorrelationService correlationService;
    private final ThreatScoringService threatScoringService;
    private final BlocklistService blocklistService;
    private final ThreatAssessmentPublisher threatAssessmentPublisher;
    private final com.honeymesh.threatengine.service.ThreatAssessmentHistoryService threatAssessmentHistoryService;

    private volatile TelemetryEvent lastEvent;
    private volatile CorrelationSnapshot lastSnapshot;
    private volatile ThreatAssessment lastAssessment;
    private volatile ThreatAssessmentEvent lastAssessmentEvent;

    public TelemetryListener(StringRedisTemplate redisTemplate,
                             CorrelationService correlationService,
                             ThreatScoringService threatScoringService,
                             BlocklistService blocklistService,
                             ThreatAssessmentPublisher threatAssessmentPublisher,
                             com.honeymesh.threatengine.service.ThreatAssessmentHistoryService threatAssessmentHistoryService) {
        this.redisTemplate = redisTemplate;
        this.correlationService = correlationService;
        this.threatScoringService = threatScoringService;
        this.blocklistService = blocklistService;
        this.threatAssessmentPublisher = threatAssessmentPublisher;
        this.threatAssessmentHistoryService = threatAssessmentHistoryService;
    }

    @RabbitListener(queues = "threat-engine.telemetry-queue")
    public void onTelemetryEvent(TelemetryEvent event) {
        this.lastEvent = event;

        // Baseline: Per-decoy atomic hit count increment
        redisTemplate.opsForValue().increment("honeymesh:telemetry:count:" + event.decoyId());

        // Phase 1: Rolling-window correlation
        CorrelationSnapshot snapshot = correlationService.correlate(event);
        this.lastSnapshot = snapshot;

        // Phase 2: Threat scoring assessment
        ThreatAssessment assessment = threatScoringService.assess(snapshot);
        this.lastAssessment = assessment;

        // Phase 3: Temporary Redis blocklisting for CRITICAL threat level
        boolean isBlocked = false;
        Instant blockExpiresAt = null;
        if (assessment.level() == ThreatLevel.CRITICAL) {
            blocklistService.block(event.sourceIp());
            isBlocked = true;
            blockExpiresAt = Instant.now().plusSeconds(BlocklistService.DEFAULT_BLOCK_TTL_SECONDS);
            log.warn("CRITICAL threat detected for IP {}: Block entry created/refreshed in Redis", event.sourceIp());
        } else {
            log.info("Threat Assessment for IP {}: score={}, level={}, reasons={}",
                    event.sourceIp(), assessment.score(), assessment.level(), assessment.reasons());
        }

        // Phase 4: Construct ThreatAssessmentEvent integration message
        ThreatAssessmentEvent assessmentEvent = new ThreatAssessmentEvent(
                UUID.randomUUID().toString(),
                event.sourceIp(),
                event.decoyId(),
                event.endpoint(),
                assessment.score(),
                assessment.level(),
                assessment.reasons(),
                snapshot.recentHitCount(),
                snapshot.distinctDecoyCount(),
                isBlocked,
                blockExpiresAt,
                Instant.now()
        );
        this.lastAssessmentEvent = assessmentEvent;

        // Phase 6: Store in Redis bounded recent-history list (max 50) for dashboard
        threatAssessmentHistoryService.saveRecentAssessment(assessmentEvent);

        // Phase 4: Publish integration event to RabbitMQ
        threatAssessmentPublisher.publish(assessmentEvent);
    }


    public TelemetryEvent getLastEvent() {
        return lastEvent;
    }

    public CorrelationSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public ThreatAssessment getLastAssessment() {
        return lastAssessment;
    }

    public ThreatAssessmentEvent getLastAssessmentEvent() {
        return lastAssessmentEvent;
    }
}




