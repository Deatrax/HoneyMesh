package com.honeymesh.incident.event;

import java.time.Instant;
import java.util.List;

public record ThreatAssessmentEvent(
        String assessmentId,
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
        Instant assessedAt
) {}
