package com.honeymesh.threatengine.event;

import com.honeymesh.threatengine.model.RiskLevel;
import java.time.Instant;

public record TelemetryEvent(
                String decoyId,
                String sourceIp,
                String endpoint,
                RiskLevel riskLevel,
                Instant occurredAt) {
}
