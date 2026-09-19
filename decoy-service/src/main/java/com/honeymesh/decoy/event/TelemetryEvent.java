package com.honeymesh.decoy.event;

import java.time.Instant;

import com.honeymesh.decoy.entity.RiskLevel;


public record TelemetryEvent(
        String decoyId,
        String sourceIp,
        String endpoint,
        RiskLevel riskLevel,
        Instant occurredAt
) {}