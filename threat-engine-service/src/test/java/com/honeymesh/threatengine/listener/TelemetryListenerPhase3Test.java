package com.honeymesh.threatengine.listener;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.RiskLevel;
import com.honeymesh.threatengine.model.ThreatAssessment;
import com.honeymesh.threatengine.model.ThreatLevel;
import com.honeymesh.threatengine.publisher.ThreatAssessmentPublisher;
import com.honeymesh.threatengine.service.BlocklistService;
import com.honeymesh.threatengine.service.CorrelationService;
import com.honeymesh.threatengine.service.ThreatScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryListenerPhase3Test {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CorrelationService correlationService;

    @Mock
    private ThreatScoringService threatScoringService;

    @Mock
    private BlocklistService blocklistService;

    @Mock
    private ThreatAssessmentPublisher threatAssessmentPublisher;

    @Mock
    private com.honeymesh.threatengine.service.ThreatAssessmentHistoryService historyService;

    @Mock
    private com.honeymesh.threatengine.service.TelemetryIdempotencyService idempotencyService;

    private TelemetryListener telemetryListener;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(idempotencyService.tryClaim(any())).thenReturn(true);
        telemetryListener = new TelemetryListener(
                redisTemplate,
                correlationService,
                threatScoringService,
                blocklistService,
                threatAssessmentPublisher,
                historyService,
                idempotencyService
        );
    }

    @Test
    @DisplayName("Case 1: INFORMATIONAL level -> no Redis block created")
    void testInformationalDoesNotTriggerBlock() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.1", "/test", RiskLevel.LOW, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(1, 1, RiskLevel.LOW);
        ThreatAssessment assessment = new ThreatAssessment(15, ThreatLevel.INFORMATIONAL, List.of("Low risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        verify(blocklistService, never()).block(anyString());
    }

    @Test
    @DisplayName("Case 2: SUSPICIOUS level -> no Redis block created")
    void testSuspiciousDoesNotTriggerBlock() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.2", "/test", RiskLevel.MEDIUM, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(3, 1, RiskLevel.MEDIUM);
        ThreatAssessment assessment = new ThreatAssessment(40, ThreatLevel.SUSPICIOUS, List.of("Medium risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        verify(blocklistService, never()).block(anyString());
    }

    @Test
    @DisplayName("Case 3: HIGH level -> no Redis block created")
    void testHighDoesNotTriggerBlock() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.3", "/test", RiskLevel.HIGH, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(3, 2, RiskLevel.HIGH);
        ThreatAssessment assessment = new ThreatAssessment(65, ThreatLevel.HIGH, List.of("High risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        verify(blocklistService, never()).block(anyString());
    }

    @Test
    @DisplayName("Case 4: CRITICAL level -> triggers BlocklistService.block(sourceIp)")
    void testCriticalTriggersBlock() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "203.0.113.7", "/test", RiskLevel.CRITICAL, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(5, 2, RiskLevel.CRITICAL);
        ThreatAssessment assessment = new ThreatAssessment(90, ThreatLevel.CRITICAL, List.of("Critical risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        verify(blocklistService).block("203.0.113.7");
    }
}
