package com.honeymesh.incident.dto;

import com.honeymesh.incident.entity.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(
        @NotNull IncidentStatus status,
        // Optimistic-locking guard: the version the client last saw for
        // this incident. If someone else changed it in the meantime, this
        // won't match the current row anymore and the update is rejected
        // with 409 instead of silently overwriting their change.
        @NotNull Long version
) {}
