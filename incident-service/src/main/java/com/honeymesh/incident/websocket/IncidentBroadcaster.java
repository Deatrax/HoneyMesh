package com.honeymesh.incident.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honeymesh.incident.dto.IncidentResponse;
import com.honeymesh.incident.entity.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Turns an Incident into the small JSON envelope the React dashboard
 * listens for on /ws/alerts, then hands it to the existing
 * AlertWebSocketHandler to actually push it over the wire. Kept separate
 * from IncidentService so that class stays focused on business rules,
 * not message formatting — same reasoning as ThreatAssessmentPublisher
 * being its own class in threat-engine-service instead of being inlined
 * into TelemetryListener.
 *
 * Spring Boot auto-configures an ObjectMapper bean for you the moment
 * Jackson is on the classpath (it already is, via spring-boot-starter-web)
 * — we don't have to create or configure one ourselves.
 */
@Component
public class IncidentBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(IncidentBroadcaster.class);

    private final AlertWebSocketHandler socketHandler;
    private final ObjectMapper objectMapper;

    public IncidentBroadcaster(AlertWebSocketHandler socketHandler, ObjectMapper objectMapper) {
        this.socketHandler = socketHandler;
        this.objectMapper = objectMapper;
    }

    public void incidentUpserted(Incident incident) {
        Map<String, Object> payload = Map.of(
                "type", "incident.updated",
                "incident", IncidentResponse.from(incident)
        );
        send(payload);
    }

    private void send(Map<String, Object> payload) {
        try {
            socketHandler.broadcast(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize WebSocket broadcast payload", e);
        }
    }
}
