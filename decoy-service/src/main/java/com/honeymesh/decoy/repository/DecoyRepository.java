package com.honeymesh.decoy.repository;

import com.honeymesh.decoy.entity.Decoy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DecoyRepository extends JpaRepository<Decoy, Long> {

    // The honeypot controller's core lookup: "is this incoming path a
    // configured decoy?" — this is what makes decoys admin-configurable
    // instead of hardcoded.
    Optional<Decoy> findByEndpointPath(String endpointPath);
}
