package com.honeymesh.decoy.dto;

import java.time.Instant;
import java.util.Map;

public record RequestForensics(
        String sourceIp,
        String method,
        String uri,
        Map<String, String> headers,
        Instant capturedAt
) {}
