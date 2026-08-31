package com.honeymesh.decoy.dto;

import com.honeymesh.decoy.entity.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// What the admin API accepts to create a decoy. Deliberately separate from
// the Decoy entity — the request shape (no id, no createdAt, no enabled
// flag to set on creation) shouldn't be dictated by the database schema.
public record DecoyRequest(
        @NotBlank String name,
        @NotBlank String endpointPath,
        @NotNull RiskLevel riskLevel,
        String orgId
) {}
