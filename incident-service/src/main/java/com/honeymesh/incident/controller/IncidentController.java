package com.honeymesh.incident.controller;

import com.honeymesh.incident.dto.AssignRequest;
import com.honeymesh.incident.dto.IncidentActivityResponse;
import com.honeymesh.incident.dto.IncidentResponse;
import com.honeymesh.incident.dto.NoteRequest;
import com.honeymesh.incident.dto.PermaBlockRequest;
import com.honeymesh.incident.dto.StatusChangeRequest;
import com.honeymesh.incident.dto.UnblockRequest;
import com.honeymesh.incident.entity.Incident;
import com.honeymesh.incident.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    // Kept public/unauthenticated on purpose — scripts/verify.sh checks
    // "gateway routes /api/incidents/ping" without sending a token, the
    // same way it does for the other three services. See SecurityConfig.
    @GetMapping("/ping")
    public String ping() {
        return "incident-service is up";
    }

    @GetMapping
    public List<IncidentResponse> list() {
        return incidentService.findAll().stream().map(IncidentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable Long id) {
        return IncidentResponse.from(incidentService.findById(id));
    }

    @GetMapping("/{id}/activity")
    public List<IncidentActivityResponse> activity(@PathVariable Long id) {
        return incidentService.findActivity(id).stream().map(IncidentActivityResponse::from).toList();
    }

    // Authentication is a method parameter here, not something we read
    // ourselves — Spring MVC resolves it automatically from whatever
    // JwtAuthFilter put into the SecurityContext for this request. Using
    // authentication.getName() (not a field in the request body) means a
    // caller can't spoof who's adding the note; it's always the identity
    // proven by their token.
    @PostMapping("/{id}/notes")
    public IncidentResponse addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                     Authentication authentication) {
        Incident incident = incidentService.addNote(id, authentication.getName(), request.message());
        return IncidentResponse.from(incident);
    }

    @PatchMapping("/{id}/status")
    public IncidentResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                          Authentication authentication) {
        Incident incident = incidentService.changeStatus(id, request.status(), request.version(), authentication.getName());
        return IncidentResponse.from(incident);
    }

    // Restricted to ROLE_ADMIN — see SecurityConfig's
    // .requestMatchers(HttpMethod.PATCH, "/api/incidents/*/assign").hasRole("ADMIN").
    @PatchMapping("/{id}/assign")
    public IncidentResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request,
                                    Authentication authentication) {
        Incident incident = incidentService.assign(id, request.analyst(), request.version(), authentication.getName());
        return IncidentResponse.from(incident);
    }

    // Also admin-only — see SecurityConfig's matching rule for this path.
    // A manual override on top of the 5-minute auto-expiry, not a
    // replacement for it.
    @PatchMapping("/{id}/unblock")
    public IncidentResponse unblock(@PathVariable Long id, @Valid @RequestBody UnblockRequest request,
                                     Authentication authentication) {
        Incident incident = incidentService.unblock(id, request.version(), authentication.getName());
        return IncidentResponse.from(incident);
    }

    // Admin-only, same as unblock. See IncidentService.permaBlock() for
    // the actual "no TTL" mechanics.
    @PatchMapping("/{id}/perma-block")
    public IncidentResponse permaBlock(@PathVariable Long id, @Valid @RequestBody PermaBlockRequest request,
                                        Authentication authentication) {
        Incident incident = incidentService.permaBlock(id, request.version(), authentication.getName());
        return IncidentResponse.from(incident);
    }
}
