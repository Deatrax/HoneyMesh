package com.honeymesh.incident.dto;

import com.honeymesh.incident.entity.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(
        @NotNull IncidentStatus status,
        @NotNull Long version
) {}
