package com.honeymesh.incident.event;

// Local copy of Threat Engine's ThreatLevel enum — same 4 values, same
// order, different package. This is deliberate: see ThreatAssessmentEvent
// in this same package for why we never import Prince's actual class.
//
// The declared order matters here, not just the names: IncidentService
// compares levels using ordinal() (INFORMATIONAL < SUSPICIOUS < HIGH <
// CRITICAL) to decide whether an assessment is serious enough to open an
// incident. If you ever reorder this enum, that comparison breaks.
public enum ThreatLevel {
    INFORMATIONAL,
    SUSPICIOUS,
    HIGH,
    CRITICAL
}
