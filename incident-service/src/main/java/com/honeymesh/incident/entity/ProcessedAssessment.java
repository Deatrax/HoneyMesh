package com.honeymesh.incident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// One row per ThreatAssessmentEvent we've already handled. assessmentId
// is the @Id (the primary key), so a second attempt to insert the same
// id throws a DataIntegrityViolationException instead of silently
// succeeding twice — that's what makes this an idempotency guard rather
// than just a log table.
//
// Prince's Threat Engine uses Redis SET-NX for the same idea on the
// telemetry side. We use a Postgres unique key instead, since Incident
// Service is already all-in on JPA/Postgres and this avoids introducing
// a second pattern for the same concept.
@Entity
@Table(name = "processed_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedAssessment {

    @Id
    @Column(name = "assessment_id")
    private String assessmentId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
