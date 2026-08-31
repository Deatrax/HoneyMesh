package com.honeymesh.incident.entity;

import com.honeymesh.incident.event.ThreatLevel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.FetchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The attacker. This is the field we correlate on: as long as an
    // incident for this IP is still open (not RESOLVED), new assessments
    // update it instead of spawning a fresh incident for every hit.
    @Column(name = "source_ip", nullable = false)
    private String sourceIp;

    @Column(name = "triggering_decoy_id")
    private String triggeringDecoyId;

    @Column(name = "triggering_endpoint")
    private String triggeringEndpoint;

    @Column(nullable = false)
    private int score;

    // We reuse Threat Engine's own ThreatLevel enum here instead of
    // inventing a second one — it's the same concept (how bad is this),
    // so there's no reason to have two enums that must always be kept in
    // sync by hand.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThreatLevel level;

    // @ElementCollection makes Hibernate create and manage a second table
    // ("incident_reasons") automatically — one row per reason string,
    // linked back to this incident by incident_id. We just work with a
    // plain List<String> in Java; Hibernate handles the join table.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_reasons", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "reason", length = 500)
    @Builder.Default
    private List<String> reasons = new ArrayList<>();

    @Column(name = "recent_hit_count")
    private int recentHitCount;

    @Column(name = "distinct_decoy_count")
    private int distinctDecoyCount;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "block_expires_at")
    private Instant blockExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "assigned_analyst")
    private String assignedAnalyst;

    // Traceability only — "which assessment most recently touched this
    // incident". This is NOT the idempotency guard; that's the separate
    // ProcessedAssessment table, which stops the exact same RabbitMQ
    // message from being processed twice.
    @Column(name = "last_assessment_id")
    private String lastAssessmentId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Optimistic locking. Hibernate stamps this 0 on insert and bumps it
    // by 1 on every successful update, automatically — we never set it
    // ourselves. See IncidentService.checkVersion()/flushOrConflict() for
    // how it's actually used to reject stale writes with a 409 instead of
    // silently overwriting someone else's change.
    @Version
    private Long version;
}
