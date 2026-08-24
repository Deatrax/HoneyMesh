package com.honeymesh.incident.dto;

import com.honeymesh.incident.entity.IncidentActivity;

import java.time.Instant;

public record IncidentActivityResponse(
        Long id,
        String author,
        String message,
        Instant createdAt
) {
    public static IncidentActivityResponse from(IncidentActivity activity) {
        return new IncidentActivityResponse(
                activity.getId(),
                activity.getAuthor(),
                activity.getMessage(),
                activity.getCreatedAt()
        );
    }
}
