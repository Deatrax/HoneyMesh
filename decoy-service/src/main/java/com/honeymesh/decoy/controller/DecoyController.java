package com.honeymesh.decoy.controller;

import com.honeymesh.decoy.config.RabbitConfig;
import com.honeymesh.decoy.event.TelemetryEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/decoy")
public class DecoyController {

    private final RabbitTemplate rabbitTemplate;

    public DecoyController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping("/ping")
    public String ping() {
        return "decoy-service is up";
    }

    /**
     * Temporary proof-of-life endpoint for the walking skeleton.
     * Fires a fake telemetry event through RabbitMQ so you can watch it show up
     * on the threat-engine side. Delete once real decoy-hit recording exists.
     */
    @PostMapping("/test-event")
    public TelemetryEvent publishTestEvent(
            @RequestParam(defaultValue = "decoy-001") String decoyId,
            @RequestParam(defaultValue = "203.0.113.7") String sourceIp
    ) {
        TelemetryEvent event = new TelemetryEvent(decoyId, sourceIp, "/api/admin/db-backup", Instant.now());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.TELEMETRY_ROUTING_KEY, event);
        return event;
    }
}
