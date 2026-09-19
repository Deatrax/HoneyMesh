package com.honeymesh.decoy.controller;

import com.honeymesh.decoy.dto.DecoyRequest;
import com.honeymesh.decoy.dto.DecoyResponse;
import com.honeymesh.decoy.dto.RequestForensics;
import com.honeymesh.decoy.service.DecoyService;
import com.honeymesh.decoy.service.RequestForensicsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/decoy/admin")
public class DecoyAdminController {

    private final DecoyService decoyService;
    private final RequestForensicsService forensicsService;

    public DecoyAdminController(DecoyService decoyService, RequestForensicsService forensicsService) {
        this.decoyService = decoyService;
        this.forensicsService = forensicsService;
    }

    @GetMapping
    public List<DecoyResponse> list() {
        return decoyService.findAll().stream().map(DecoyResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DecoyResponse get(@PathVariable Long id) {
        return DecoyResponse.from(decoyService.findById(id));
    }

    @GetMapping("/hit-detail/{sourceIp}")
    public ResponseEntity<RequestForensics> hitDetail(@PathVariable String sourceIp) {
        return forensicsService.findBySourceIp(sourceIp)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DecoyResponse create(@Valid @RequestBody DecoyRequest request) {
        return DecoyResponse.from(decoyService.create(request));
    }

    @PatchMapping("/{id}/enable")
    public DecoyResponse enable(@PathVariable Long id) {
        return DecoyResponse.from(decoyService.setEnabled(id, true));
    }

    @PatchMapping("/{id}/disable")
    public DecoyResponse disable(@PathVariable Long id) {
        return DecoyResponse.from(decoyService.setEnabled(id, false));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        decoyService.delete(id);
    }
}
