package com.honeymesh.incident.dto;

import jakarta.validation.constraints.NotNull;

public record UnblockRequest(
        @NotNull Long version
) {}
