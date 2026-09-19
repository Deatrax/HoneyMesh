package com.honeymesh.decoy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "decoys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Decoy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "endpoint_path", nullable = false, unique = true)
    private String endpointPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "org_id")
    private String orgId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
