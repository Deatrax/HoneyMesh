package com.honeymesh.incident.repository;

import com.honeymesh.incident.entity.ProcessedAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

// No custom query methods needed — IncidentService only ever does
// save() (to claim an assessmentId) and relies on the primary key
// constraint to reject duplicates. See IncidentService.claimAssessment().
public interface ProcessedAssessmentRepository extends JpaRepository<ProcessedAssessment, String> {
}
