package com.honeymesh.incident.dto;

import com.honeymesh.incident.entity.Incident;
import com.honeymesh.incident.entity.IncidentStatus;
import com.honeymesh.incident.event.ThreatLevel;

import java.time.Instant;
import java.util.List;

public record IncidentResponse(
        Long id,
        String sourceIp,
        String triggeringDecoyId,
        String triggeringEndpoint,
        int score,
        ThreatLevel level,
        List<String> reasons,
        int recentHitCount,
        int distinctDecoyCount,
        boolean blocked,
        Instant blockExpiresAt,
        IncidentStatus status,
        String assignedAnalyst,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getSourceIp(),
                incident.getTriggeringDecoyId(),
                incident.getTriggeringEndpoint(),
                incident.getScore(),
                incident.getLevel(),
                incident.getReasons(),
                incident.getRecentHitCount(),
                incident.getDistinctDecoyCount(),
                incident.isBlocked(),
                incident.getBlockExpiresAt(),
                incident.getStatus(),
                incident.getAssignedAnalyst(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getVersion()
        );
    }
}
