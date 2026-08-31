package com.honeymesh.decoy.dto;

import java.time.Instant;
import java.util.Map;

// Full request detail for the most recent hit from a given source IP —
// method, path+query string, and every header the client actually sent.
// This is application-layer forensics (what HttpServletRequest already
// gives us), not packet capture — no raw network bytes, no OS-level
// socket access, a different technical domain entirely.
public record RequestForensics(
        String sourceIp,
        String method,
        String uri,
        Map<String, String> headers,
        Instant capturedAt
) {}
