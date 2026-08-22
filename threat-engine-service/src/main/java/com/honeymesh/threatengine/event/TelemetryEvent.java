package com.honeymesh.threatengine.event;

import java.time.Instant;

// Same JSON shape as decoy-service's TelemetryEvent, on purpose. See that
// class's javadoc for why this isn't a shared library class instead.
public record TelemetryEvent(
        String decoyId,
        String sourceIp,
        String endpoint,
        Instant occurredAt
) {}
