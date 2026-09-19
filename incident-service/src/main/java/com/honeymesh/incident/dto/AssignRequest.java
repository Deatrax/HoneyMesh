package com.honeymesh.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignRequest(
        @NotBlank String analyst,
        @NotNull Long version
) {}
