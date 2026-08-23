package com.honeymesh.decoy.dto;

import com.honeymesh.decoy.entity.Decoy;
import com.honeymesh.decoy.entity.RiskLevel;

import java.time.Instant;

// What the admin API returns. Separate from the entity so React never has
// to know or care about JPA internals, and so adding an internal-only field
// to Decoy later doesn't automatically leak it over the API.
public record DecoyResponse(
        Long id,
        String name,
        String endpointPath,
        RiskLevel riskLevel,
        boolean enabled,
        String orgId,
        Instant createdAt
) {
    public static DecoyResponse from(Decoy decoy) {
        return new DecoyResponse(
                decoy.getId(),
                decoy.getName(),
                decoy.getEndpointPath(),
                decoy.getRiskLevel(),
                decoy.isEnabled(),
                decoy.getOrgId(),
                decoy.getCreatedAt()
        );
    }
}
