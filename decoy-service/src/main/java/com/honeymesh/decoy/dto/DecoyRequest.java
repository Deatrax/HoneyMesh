package com.honeymesh.decoy.dto;

import com.honeymesh.decoy.entity.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DecoyRequest(
        @NotBlank String name,
        @NotBlank String endpointPath,
        @NotNull RiskLevel riskLevel,
        String orgId
) {}
