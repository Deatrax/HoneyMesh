package com.honeymesh.threatengine.model;

public record SourceCorrelationResponse(
        String sourceIp,
        int recentHitCount,
        int distinctDecoyCount,
        RiskLevel highestRiskLevel,
        boolean blocked
) {}
