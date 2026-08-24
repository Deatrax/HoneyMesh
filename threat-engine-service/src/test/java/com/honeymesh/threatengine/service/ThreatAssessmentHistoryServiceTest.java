package com.honeymesh.threatengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import com.honeymesh.threatengine.model.ThreatLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreatAssessmentHistoryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;
    private ThreatAssessmentHistoryService historyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        historyService = new ThreatAssessmentHistoryService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("saveRecentAssessment uses LPUSH and LTRIM 0 49 to keep list bounded at 50")
    void testSaveRecentAssessment() {
        ThreatAssessmentEvent event = new ThreatAssessmentEvent(
                UUID.randomUUID().toString(),
                "203.0.113.7",
                "decoy-1",
                "/api/test",
                85,
                ThreatLevel.CRITICAL,
                List.of("Critical risk"),
                5,
                3,
                true,
                Instant.now().plusSeconds(300),
                Instant.now()
        );

        historyService.saveRecentAssessment(event);

        verify(listOperations).leftPush(eq(ThreatAssessmentHistoryService.RECENT_ASSESSMENTS_KEY), anyString());
        verify(listOperations).trim(eq(ThreatAssessmentHistoryService.RECENT_ASSESSMENTS_KEY), eq(0L), eq(49L));
    }

    @Test
    @DisplayName("getLatestAssessment returns null when Redis history list is empty")
    void testGetLatestAssessmentEmpty() {
        when(listOperations.index(ThreatAssessmentHistoryService.RECENT_ASSESSMENTS_KEY, 0)).thenReturn(null);

        ThreatAssessmentEvent result = historyService.getLatestAssessment();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getRecentAssessments returns parsed ThreatAssessmentEvents from Redis list")
    void testGetRecentAssessments() throws Exception {
        ThreatAssessmentEvent event1 = new ThreatAssessmentEvent(
                "id-1", "10.0.0.1", "decoy-1", "/p1", 15, ThreatLevel.INFORMATIONAL, List.of("Low"), 1, 1, false, null, Instant.now()
        );
        ThreatAssessmentEvent event2 = new ThreatAssessmentEvent(
                "id-2", "10.0.0.2", "decoy-2", "/p2", 90, ThreatLevel.CRITICAL, List.of("Critical"), 5, 2, true, Instant.now().plusSeconds(300), Instant.now()
        );

        String json1 = objectMapper.writeValueAsString(event1);
        String json2 = objectMapper.writeValueAsString(event2);

        when(listOperations.range(ThreatAssessmentHistoryService.RECENT_ASSESSMENTS_KEY, 0, 49))
                .thenReturn(List.of(json1, json2));

        List<ThreatAssessmentEvent> events = historyService.getRecentAssessments();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).assessmentId()).isEqualTo("id-1");
        assertThat(events.get(1).assessmentId()).isEqualTo("id-2");
        assertThat(events.get(1).level()).isEqualTo(ThreatLevel.CRITICAL);
    }
}
