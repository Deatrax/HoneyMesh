package com.honeymesh.incident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// One row per event in an incident's history — this doubles as both the
// "notes" feature and the "audit history" requirement, instead of being
// two separate tables. author = "system" for automatic entries (created,
// status changed, assigned), or the logged-in analyst's username for a
// manual note they typed.
@Entity
@Table(name = "incident_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
