package com.honeymesh.incident.repository;

import com.honeymesh.incident.entity.IncidentActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentActivityRepository extends JpaRepository<IncidentActivity, Long> {
    List<IncidentActivity> findAllByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
