package com.honeymesh.decoy.event;

import java.time.Instant;

/**
 * The event CONTRACT, not a shared class. threat-engine-service has its own
 * copy of this same shape in its own package — that's deliberate (see README:
 * "why isn't this a shared library?"). Services agree on JSON shape, not on
 * Java class identity.
 */
public record TelemetryEvent(
        String decoyId,
        String sourceIp,
        String endpoint,
        Instant occurredAt
) {}
