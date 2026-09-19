package com.honeymesh.incident.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TestAlertBroadcaster {

    private final AlertWebSocketHandler handler;

    public TestAlertBroadcaster(AlertWebSocketHandler handler) {
        this.handler = handler;
    }

    @Scheduled(fixedRate = 10000)
    public void broadcastHeartbeat() {
        handler.broadcast("{\"type\":\"heartbeat\",\"ts\":\"" + Instant.now() + "\"}");
    }
}
