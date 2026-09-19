package com.honeymesh.threatengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ThreatAssessmentHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ThreatAssessmentHistoryService.class);

    public static final String RECENT_ASSESSMENTS_KEY = "honeymesh:threat:recent-assessments";
    public static final int MAX_RECENT_ASSESSMENTS = 50;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ThreatAssessmentHistoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveRecentAssessment(ThreatAssessmentEvent event) {
        if (event == null) return;
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.opsForList().leftPush(RECENT_ASSESSMENTS_KEY, json);
            redisTemplate.opsForList().trim(RECENT_ASSESSMENTS_KEY, 0, MAX_RECENT_ASSESSMENTS - 1);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ThreatAssessmentEvent to JSON for Redis history", e);
        }
    }

    public ThreatAssessmentEvent getLatestAssessment() {
        String json = redisTemplate.opsForList().index(RECENT_ASSESSMENTS_KEY, 0);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ThreatAssessmentEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize latest ThreatAssessmentEvent from Redis", e);
            return null;
        }
    }

    public List<ThreatAssessmentEvent> getRecentAssessments() {
        List<String> rawJsonList = redisTemplate.opsForList().range(RECENT_ASSESSMENTS_KEY, 0, MAX_RECENT_ASSESSMENTS - 1);
        if (rawJsonList == null || rawJsonList.isEmpty()) {
            return Collections.emptyList();
        }

        List<ThreatAssessmentEvent> events = new ArrayList<>();
        for (String json : rawJsonList) {
            try {
                events.add(objectMapper.readValue(json, ThreatAssessmentEvent.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize ThreatAssessmentEvent item from Redis list", e);
            }
        }
        return events;
    }
}
