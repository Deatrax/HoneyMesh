package com.honeymesh.incident.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Temporary proof-of-life: pushes a heartbeat every 10s so you can confirm a
 * WebSocket client connected through the gateway actually receives messages.
 * Delete once real incident-created / status-changed events replace it.
 */
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
