package com.honeymesh.incident.dto;

import jakarta.validation.constraints.NotNull;

public record UnblockRequest(
        // Same optimistic-locking guard every other mutating action here
        // uses — see StatusChangeRequest/AssignRequest.
        @NotNull Long version
) {}
