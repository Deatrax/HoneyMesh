package com.honeymesh.threatengine.listener;

import com.honeymesh.threatengine.event.TelemetryEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TelemetryListener {

    private final StringRedisTemplate redisTemplate;
    private volatile TelemetryEvent lastEvent;

    public TelemetryListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = "threat-engine.telemetry-queue")
    public void onTelemetryEvent(TelemetryEvent event) {
        this.lastEvent = event;
        // Atomic Redis counter -> this exact pattern (INCR under concurrent writers)
        // is the "flash sale ticket counter" pattern from lecture, reused here for
        // per-decoy hit counting. This is your Day 2 seed for real scoring logic.
        redisTemplate.opsForValue().increment("honeymesh:telemetry:count:" + event.decoyId());
    }

    public TelemetryEvent getLastEvent() {
        return lastEvent;
    }
}
