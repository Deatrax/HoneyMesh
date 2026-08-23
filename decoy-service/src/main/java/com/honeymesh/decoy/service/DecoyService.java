package com.honeymesh.decoy.service;

import com.honeymesh.decoy.dto.DecoyRequest;
import com.honeymesh.decoy.entity.Decoy;
import com.honeymesh.decoy.repository.DecoyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Business logic lives here, not in the controller — this is the
// controller/service/repository layering from lecture, and it's also just
// the right place for "reject duplicate endpoint paths" to live, since
// that's a rule, not plumbing.
@Service
public class DecoyService {

    private final DecoyRepository decoyRepository;

    public DecoyService(DecoyRepository decoyRepository) {
        this.decoyRepository = decoyRepository;
    }

    public List<Decoy> findAll() {
        return decoyRepository.findAll();
    }

    public Decoy findById(Long id) {
        return decoyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decoy " + id + " not found"));
    }

    public Optional<Decoy> findByEndpointPath(String endpointPath) {
        return decoyRepository.findByEndpointPath(endpointPath);
    }

    @Transactional
    public Decoy create(DecoyRequest request) {
        decoyRepository.findByEndpointPath(request.endpointPath()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A decoy already exists at endpoint " + request.endpointPath());
        });

        Decoy decoy = Decoy.builder()
                .name(request.name())
                .endpointPath(request.endpointPath())
                .riskLevel(request.riskLevel())
                .orgId(request.orgId())
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        return decoyRepository.save(decoy);
    }

    @Transactional
    public Decoy setEnabled(Long id, boolean enabled) {
        Decoy decoy = findById(id);
        decoy.setEnabled(enabled);
        return decoyRepository.save(decoy);
    }

    @Transactional
    public void delete(Long id) {
        decoyRepository.delete(findById(id));
    }
}
