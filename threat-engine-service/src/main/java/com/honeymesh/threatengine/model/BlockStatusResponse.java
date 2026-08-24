package com.honeymesh.threatengine.model;

public record BlockStatusResponse(
        String sourceIp,
        boolean blocked,
        long remainingTtlSeconds
) {}
