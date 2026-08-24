package com.honeymesh.threatengine.model;

public record CorrelationSnapshot(
        int recentHitCount,
        int distinctDecoyCount,
        RiskLevel highestRiskLevel
) {}
