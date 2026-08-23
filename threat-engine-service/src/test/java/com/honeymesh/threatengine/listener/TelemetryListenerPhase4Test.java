package com.honeymesh.threatengine.listener;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryListenerPhase4Test {

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

    private TelemetryListener telemetryListener;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        telemetryListener = new TelemetryListener(
                redisTemplate,
                correlationService,
                threatScoringService,
                blocklistService,
                threatAssessmentPublisher
        );
    }

    @Test
    @DisplayName("Case 1: INFORMATIONAL assessment publishes ThreatAssessmentEvent with blocked=false and null blockExpiresAt")
    void testInformationalAssessmentPublishing() {
        TelemetryEvent event = new TelemetryEvent("decoy-101", "192.168.1.5", "/login", RiskLevel.LOW, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(1, 1, RiskLevel.LOW);
        ThreatAssessment assessment = new ThreatAssessment(15, ThreatLevel.INFORMATIONAL, List.of("Low risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        ArgumentCaptor<ThreatAssessmentEvent> captor = ArgumentCaptor.forClass(ThreatAssessmentEvent.class);
        verify(threatAssessmentPublisher).publish(captor.capture());

        ThreatAssessmentEvent published = captor.getValue();
        assertThat(published.level()).isEqualTo(ThreatLevel.INFORMATIONAL);
        assertThat(published.score()).isEqualTo(15);
        assertThat(published.reasons()).containsExactly("Low risk");
        assertThat(published.blocked()).isFalse();
        assertThat(published.blockExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Case 2: HIGH assessment publishes ThreatAssessmentEvent with level=HIGH and blocked=false")
    void testHighAssessmentPublishing() {
        TelemetryEvent event = new TelemetryEvent("decoy-102", "192.168.1.10", "/db", RiskLevel.HIGH, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(3, 2, RiskLevel.HIGH);
        ThreatAssessment assessment = new ThreatAssessment(65, ThreatLevel.HIGH, List.of("High risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        ArgumentCaptor<ThreatAssessmentEvent> captor = ArgumentCaptor.forClass(ThreatAssessmentEvent.class);
        verify(threatAssessmentPublisher).publish(captor.capture());

        ThreatAssessmentEvent published = captor.getValue();
        assertThat(published.level()).isEqualTo(ThreatLevel.HIGH);
        assertThat(published.score()).isEqualTo(65);
        assertThat(published.blocked()).isFalse();
        assertThat(published.blockExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Case 3: CRITICAL assessment publishes ThreatAssessmentEvent with blocked=true and non-null blockExpiresAt")
    void testCriticalAssessmentPublishing() {
        TelemetryEvent event = new TelemetryEvent("decoy-103", "203.0.113.7", "/admin", RiskLevel.CRITICAL, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(5, 3, RiskLevel.CRITICAL);
        ThreatAssessment assessment = new ThreatAssessment(90, ThreatLevel.CRITICAL, List.of("Critical risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        verify(blocklistService).block("203.0.113.7");

        ArgumentCaptor<ThreatAssessmentEvent> captor = ArgumentCaptor.forClass(ThreatAssessmentEvent.class);
        verify(threatAssessmentPublisher).publish(captor.capture());

        ThreatAssessmentEvent published = captor.getValue();
        assertThat(published.level()).isEqualTo(ThreatLevel.CRITICAL);
        assertThat(published.score()).isEqualTo(90);
        assertThat(published.blocked()).isTrue();
        assertThat(published.blockExpiresAt()).isNotNull();
        assertThat(published.blockExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Case 4 & 5: Correlation fields and triggering event fields are correctly mapped")
    void testCorrelationAndTriggeringFieldsMapping() {
        TelemetryEvent event = new TelemetryEvent("decoy-77", "10.0.5.99", "/api/v1/auth", RiskLevel.MEDIUM, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(4, 2, RiskLevel.MEDIUM);
        ThreatAssessment assessment = new ThreatAssessment(40, ThreatLevel.SUSPICIOUS, List.of("Medium risk"));

        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event);

        ArgumentCaptor<ThreatAssessmentEvent> captor = ArgumentCaptor.forClass(ThreatAssessmentEvent.class);
        verify(threatAssessmentPublisher).publish(captor.capture());

        ThreatAssessmentEvent published = captor.getValue();
        // Triggering event fields
        assertThat(published.sourceIp()).isEqualTo("10.0.5.99");
        assertThat(published.triggeringDecoyId()).isEqualTo("decoy-77");
        assertThat(published.triggeringEndpoint()).isEqualTo("/api/v1/auth");

        // Correlation metrics
        assertThat(published.recentHitCount()).isEqualTo(4);
        assertThat(published.distinctDecoyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Case 6: Multiple telemetry events produce unique assessmentId values")
    void testUniqueAssessmentIds() {
        TelemetryEvent event1 = new TelemetryEvent("decoy-1", "10.0.0.1", "/p1", RiskLevel.LOW, Instant.now());
        TelemetryEvent event2 = new TelemetryEvent("decoy-1", "10.0.0.1", "/p2", RiskLevel.LOW, Instant.now());
        CorrelationSnapshot snapshot = new CorrelationSnapshot(1, 1, RiskLevel.LOW);
        ThreatAssessment assessment = new ThreatAssessment(15, ThreatLevel.INFORMATIONAL, List.of("Low risk"));

        when(correlationService.correlate(any())).thenReturn(snapshot);
        when(threatScoringService.assess(any())).thenReturn(assessment);

        telemetryListener.onTelemetryEvent(event1);
        telemetryListener.onTelemetryEvent(event2);

        ArgumentCaptor<ThreatAssessmentEvent> captor = ArgumentCaptor.forClass(ThreatAssessmentEvent.class);
        verify(threatAssessmentPublisher, times(2)).publish(captor.capture());

        List<ThreatAssessmentEvent> events = captor.getAllValues();
        assertThat(events.get(0).assessmentId()).isNotEqualTo(events.get(1).assessmentId());
    }
}
