package com.honeymesh.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignRequest(
        @NotBlank String analyst,
        // Same optimistic-locking guard as StatusChangeRequest — this is
        // the field that makes "two analysts claim the same incident"
        // resolve as "one wins, one gets a 409" instead of a silent
        // overwrite.
        @NotNull Long version
) {}
