package com.honeymesh.decoy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honeymesh.decoy.dto.RequestForensics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

// Stores the most recent request forensics per source IP in Redis — one
// key, overwritten on every hit, not a growing history. That's enough to
// answer "what did this attacker's request actually look like" when an
// analyst is looking at an incident right now, without needing to thread
// a third field through both event contracts into threat-engine-service
// and incident-service.
@Service
public class RequestForensicsService {

    private static final Logger log = LoggerFactory.getLogger(RequestForensicsService.class);

    private static final String KEY_PREFIX = "honeymesh:forensics:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RequestForensicsService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(RequestForensics forensics) {
        try {
            String json = objectMapper.writeValueAsString(forensics);
            redisTemplate.opsForValue().set(KEY_PREFIX + forensics.sourceIp(), json, TTL);
        } catch (JsonProcessingException e) {
            // Never let forensics capture fail the actual hit-recording path.
            log.warn("Failed to serialize RequestForensics for {}", forensics.sourceIp(), e);
        }
    }

    public Optional<RequestForensics> findBySourceIp(String sourceIp) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + sourceIp);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RequestForensics.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize RequestForensics for {}", sourceIp, e);
            return Optional.empty();
        }
    }
}
