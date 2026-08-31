package com.honeymesh.threatengine.controller;

import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import com.honeymesh.threatengine.listener.TelemetryListener;
import com.honeymesh.threatengine.model.BlockStatusResponse;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.RiskLevel;
import com.honeymesh.threatengine.model.SourceCorrelationResponse;
import com.honeymesh.threatengine.model.ThreatLevel;
import com.honeymesh.threatengine.service.BlocklistService;
import com.honeymesh.threatengine.service.CorrelationService;
import com.honeymesh.threatengine.service.ThreatAssessmentHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreatControllerPhase6Test {

    @Mock
    private TelemetryListener telemetryListener;

    @Mock
    private CorrelationService correlationService;

    @Mock
    private BlocklistService blocklistService;

    @Mock
    private ThreatAssessmentHistoryService historyService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ThreatController controller;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        controller = new ThreatController(
                telemetryListener,
                correlationService,
                blocklistService,
                historyService,
                redisTemplate
        );
    }

    @Test
    @DisplayName("Case 1: GET /api/threat/last-assessment returns 204 NO_CONTENT when history is empty")
    void testLastAssessmentEmpty() {
        when(historyService.getLatestAssessment()).thenReturn(null);

        ResponseEntity<ThreatAssessmentEvent> response = controller.lastAssessment();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Case 2: GET /api/threat/last-assessment returns latest assessment when available")
    void testLastAssessmentPresent() {
        ThreatAssessmentEvent event = new ThreatAssessmentEvent(
                "evt-101",
                "203.0.113.7",
                "decoy-1",
                "/api/admin/db",
                90,
                ThreatLevel.CRITICAL,
                List.of("Critical risk"),
                5,
                2,
                true,
                Instant.now().plusSeconds(300),
                Instant.now()
        );
        when(historyService.getLatestAssessment()).thenReturn(event);

        ResponseEntity<ThreatAssessmentEvent> response = controller.lastAssessment();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().assessmentId()).isEqualTo("evt-101");
        assertThat(response.getBody().sourceIp()).isEqualTo("203.0.113.7");
        assertThat(response.getBody().level()).isEqualTo(ThreatLevel.CRITICAL);
    }

    @Test
    @DisplayName("Case 3: GET /api/threat/recent-assessments returns list of recent assessments")
    void testRecentAssessments() {
        ThreatAssessmentEvent event1 = new ThreatAssessmentEvent("id-1", "10.0.0.1", "d1", "/p1", 15, ThreatLevel.INFORMATIONAL, List.of(), 1, 1, false, null, Instant.now());
        ThreatAssessmentEvent event2 = new ThreatAssessmentEvent("id-2", "10.0.0.2", "d2", "/p2", 65, ThreatLevel.HIGH, List.of(), 3, 2, false, null, Instant.now());

        when(historyService.getRecentAssessments()).thenReturn(List.of(event2, event1));

        List<ThreatAssessmentEvent> result = controller.recentAssessments();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).assessmentId()).isEqualTo("id-2");
    }

    @Test
    @DisplayName("Case 5 & 6: GET /api/threat/source/{sourceIp} returns source correlation & isolation")
    void testSourceCorrelation() {
        CorrelationSnapshot snapshotA = new CorrelationSnapshot(4, 2, RiskLevel.HIGH);
        when(correlationService.getCorrelationSnapshot("203.0.113.5")).thenReturn(snapshotA);
        when(blocklistService.isBlocked("203.0.113.5")).thenReturn(false);

        SourceCorrelationResponse respA = controller.sourceCorrelation("203.0.113.5");

        assertThat(respA.sourceIp()).isEqualTo("203.0.113.5");
        assertThat(respA.recentHitCount()).isEqualTo(4);
        assertThat(respA.distinctDecoyCount()).isEqualTo(2);
        assertThat(respA.highestRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(respA.blocked()).isFalse();
    }

    @Test
    @DisplayName("Case 7 & 8: GET /api/threat/block/{sourceIp} returns blocked and non-blocked status")
    void testBlockStatus() {
        // Blocked source
        when(blocklistService.isBlocked("203.0.113.7")).thenReturn(true);
        when(blocklistService.getRemainingTtlSeconds("203.0.113.7")).thenReturn(241L);

        BlockStatusResponse respBlocked = controller.blockStatus("203.0.113.7");
        assertThat(respBlocked.blocked()).isTrue();
        assertThat(respBlocked.remainingTtlSeconds()).isEqualTo(241L);

        // Non-blocked source
        when(blocklistService.isBlocked("192.168.1.1")).thenReturn(false);
        when(blocklistService.getRemainingTtlSeconds("192.168.1.1")).thenReturn(0L);

        BlockStatusResponse respUnblocked = controller.blockStatus("192.168.1.1");
        assertThat(respUnblocked.blocked()).isFalse();
        assertThat(respUnblocked.remainingTtlSeconds()).isEqualTo(0L);
    }
}
