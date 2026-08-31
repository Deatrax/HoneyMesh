package com.honeymesh.threatengine.model;

import java.util.List;

public record ThreatAssessment(
        int score,
        ThreatLevel level,
        List<String> reasons
) {}
