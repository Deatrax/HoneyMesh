package com.honeymesh.incident.repository;

import com.honeymesh.incident.entity.Incident;
import com.honeymesh.incident.entity.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstBySourceIpAndStatusNotOrderByCreatedAtDesc(String sourceIp, IncidentStatus excludedStatus);

    List<Incident> findAllByOrderByCreatedAtDesc();
}
