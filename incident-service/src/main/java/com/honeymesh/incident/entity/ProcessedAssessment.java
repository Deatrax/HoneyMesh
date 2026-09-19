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
