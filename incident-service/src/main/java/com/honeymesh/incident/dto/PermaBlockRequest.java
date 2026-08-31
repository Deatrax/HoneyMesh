package com.honeymesh.incident.dto;

import jakarta.validation.constraints.NotNull;

public record PermaBlockRequest(
        // Same optimistic-locking guard every other mutating action here
        // uses — see StatusChangeRequest/AssignRequest/UnblockRequest.
        @NotNull Long version
) {}
