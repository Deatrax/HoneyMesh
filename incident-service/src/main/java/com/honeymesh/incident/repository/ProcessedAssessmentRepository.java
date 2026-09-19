package com.honeymesh.incident.repository;

import com.honeymesh.incident.entity.ProcessedAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedAssessmentRepository extends JpaRepository<ProcessedAssessment, String> {
}
