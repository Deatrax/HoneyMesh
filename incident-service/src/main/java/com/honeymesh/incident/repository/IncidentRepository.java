package com.honeymesh.incident.repository;

import com.honeymesh.incident.entity.Incident;
import com.honeymesh.incident.entity.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    // Used by IncidentService to find an already-active incident for the
    // same attacker instead of creating a new one for every assessment.
    // Spring Data JPA builds the query from the method name: "status not
    // equal to the given status" — we pass RESOLVED, so this finds the
    // most recent OPEN/INVESTIGATING/CONTAINED incident for that IP.
    Optional<Incident> findFirstBySourceIpAndStatusNotOrderByCreatedAtDesc(String sourceIp, IncidentStatus excludedStatus);

    List<Incident> findAllByOrderByCreatedAtDesc();
}
