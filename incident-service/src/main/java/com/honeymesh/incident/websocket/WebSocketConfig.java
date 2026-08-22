package com.honeymesh.incident.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Plain (non-STOMP) WebSocket for now — enough to prove the handshake survives
 * the trip through Spring Cloud Gateway's ws:// route. Redis Pub/Sub fan-out
 * across replicas is a deliberate Day 2 addition — see README.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;

    public WebSocketConfig(AlertWebSocketHandler alertWebSocketHandler) {
        this.alertWebSocketHandler = alertWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .setAllowedOrigins("*"); // tighten before submission
    }
}
