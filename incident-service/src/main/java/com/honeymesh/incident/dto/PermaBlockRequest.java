package com.honeymesh.incident.dto;

import jakarta.validation.constraints.NotNull;

public record PermaBlockRequest(
        @NotNull Long version
) {}
