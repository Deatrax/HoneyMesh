package com.honeymesh.threatengine.controller;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.listener.TelemetryListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/threat")
public class ThreatController {

    private final TelemetryListener telemetryListener;
    private final StringRedisTemplate redisTemplate;

    public ThreatController(TelemetryListener telemetryListener, StringRedisTemplate redisTemplate) {
        this.telemetryListener = telemetryListener;
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
}
