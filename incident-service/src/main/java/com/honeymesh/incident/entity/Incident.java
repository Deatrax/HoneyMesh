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

    @Column(name = "source_ip", nullable = false)
    private String sourceIp;

    @Column(name = "triggering_decoy_id")
    private String triggeringDecoyId;

    @Column(name = "triggering_endpoint")
    private String triggeringEndpoint;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThreatLevel level;

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

    @Column(name = "last_assessment_id")
    private String lastAssessmentId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;
}
