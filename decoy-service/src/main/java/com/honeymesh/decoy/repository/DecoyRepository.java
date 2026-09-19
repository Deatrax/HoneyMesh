package com.honeymesh.decoy.repository;

import com.honeymesh.decoy.entity.Decoy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DecoyRepository extends JpaRepository<Decoy, Long> {

    Optional<Decoy> findByEndpointPath(String endpointPath);
}
