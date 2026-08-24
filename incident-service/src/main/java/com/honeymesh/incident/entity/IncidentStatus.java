package com.honeymesh.incident.entity;

// OPEN -> INVESTIGATING -> CONTAINED -> RESOLVED, with a shortcut straight
// from INVESTIGATING to RESOLVED for false positives. The rules about
// which transitions are actually allowed live in IncidentService (see
// ALLOWED_TRANSITIONS there) — this enum is just the set of valid values.
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    CONTAINED,
    RESOLVED
}
