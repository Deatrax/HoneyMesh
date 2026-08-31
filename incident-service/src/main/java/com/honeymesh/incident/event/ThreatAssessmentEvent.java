package com.honeymesh.incident.event;

import java.time.Instant;
import java.util.List;

// Incident Service's own copy of the message Prince's Threat Engine
// publishes. Field names and JSON shape must match his class exactly —
// this is the "frozen" contract from his handoff message — but this is a
// completely separate Java class living in our own package. We do NOT
// import com.honeymesh.threatengine.event.ThreatAssessmentEvent from his
// service; that class doesn't even exist on this service's classpath.
//
// Why this works even though it's a "different" class: Jackson (the JSON
// library) only cares about matching field names when it deserializes,
// not which Java class declared them. And RabbitConfig's
// Jackson2JsonMessageConverter is set to TypePrecedence.INFERRED, which
// tells it "ignore whatever class name the producer stamped on the
// message, and just build whatever type the @RabbitListener method asks
// for" — see ThreatAssessmentListener.java.
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
