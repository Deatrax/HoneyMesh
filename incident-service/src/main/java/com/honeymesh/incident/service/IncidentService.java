package com.honeymesh.incident.service;

import com.honeymesh.incident.entity.Incident;
import com.honeymesh.incident.entity.IncidentActivity;
import com.honeymesh.incident.entity.IncidentStatus;
import com.honeymesh.incident.entity.ProcessedAssessment;
import com.honeymesh.incident.event.ThreatAssessmentEvent;
import com.honeymesh.incident.event.ThreatLevel;
import com.honeymesh.incident.repository.IncidentActivityRepository;
import com.honeymesh.incident.repository.IncidentRepository;
import com.honeymesh.incident.repository.ProcessedAssessmentRepository;
import com.honeymesh.incident.websocket.IncidentBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Business logic lives here, not in the controller or the listener — same
// controller/service/repository layering DecoyService uses. This is the
// one class that knows the actual rules: which assessments matter, when
// to merge vs. create, which status transitions are legal, how
// optimistic locking gets enforced.
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    // OPEN -> INVESTIGATING -> CONTAINED -> RESOLVED, plus a shortcut
    // straight from INVESTIGATING to RESOLVED for false positives.
    // RESOLVED has no allowed next states — it's terminal.
    private static final Map<IncidentStatus, List<IncidentStatus>> ALLOWED_TRANSITIONS = Map.of(
            IncidentStatus.OPEN, List.of(IncidentStatus.INVESTIGATING),
            IncidentStatus.INVESTIGATING, List.of(IncidentStatus.CONTAINED, IncidentStatus.RESOLVED),
            IncidentStatus.CONTAINED, List.of(IncidentStatus.RESOLVED),
            IncidentStatus.RESOLVED, List.of()
    );

    private final IncidentRepository incidentRepository;
    private final IncidentActivityRepository activityRepository;
    private final ProcessedAssessmentRepository processedAssessmentRepository;
    private final IncidentBroadcaster broadcaster;

    // Only assessments at or above this level open/update an incident.
    // Threat Engine publishes one for every single decoy hit, so without
    // this filter every hit would become an incident. Injected via
    // constructor (same as every other dependency here) rather than a
    // @Value field, so this class can still be built by hand in a test
    // without Spring running.
    private final ThreatLevel minLevelForIncident;

    public IncidentService(IncidentRepository incidentRepository,
                            IncidentActivityRepository activityRepository,
                            ProcessedAssessmentRepository processedAssessmentRepository,
                            IncidentBroadcaster broadcaster,
                            @Value("${incident.auto-create.min-level:HIGH}") ThreatLevel minLevelForIncident) {
        this.incidentRepository = incidentRepository;
        this.activityRepository = activityRepository;
        this.processedAssessmentRepository = processedAssessmentRepository;
        this.broadcaster = broadcaster;
        this.minLevelForIncident = minLevelForIncident;
    }

    // ---------- Consuming assessments from Threat Engine ----------

    @Transactional
    public void handleAssessment(ThreatAssessmentEvent event) {
        if (!claimAssessment(event.assessmentId())) {
            log.info("Duplicate ThreatAssessmentEvent id={} — already processed, skipping", event.assessmentId());
            return;
        }

        // Ordinal comparison relies on ThreatLevel's declared order
        // (INFORMATIONAL, SUSPICIOUS, HIGH, CRITICAL) — see that enum.
        if (event.level().ordinal() < minLevelForIncident.ordinal()) {
            log.debug("Assessment id={} level={} is below the {} threshold — not opening an incident",
                    event.assessmentId(), event.level(), minLevelForIncident);
            return;
        }

        Incident incident = incidentRepository
                .findFirstBySourceIpAndStatusNotOrderByCreatedAtDesc(event.sourceIp(), IncidentStatus.RESOLVED)
                .map(existing -> updateFromAssessment(existing, event))
                .orElseGet(() -> createFromAssessment(event));

        broadcaster.incidentUpserted(incident);
    }

    // Redis SET-NX is Prince's tool of choice for telemetry idempotency;
    // here we lean on a Postgres unique-key insert instead, since this
    // service is already all-in on JPA/Postgres. Same idea either way:
    // let the database's own uniqueness guarantee decide who "wins",
    // instead of a check-then-act that two threads could both pass.
    private boolean claimAssessment(String assessmentId) {
        try {
            processedAssessmentRepository.save(new ProcessedAssessment(assessmentId, Instant.now()));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private Incident createFromAssessment(ThreatAssessmentEvent event) {
        Instant now = Instant.now();
        Incident incident = Incident.builder()
                .sourceIp(event.sourceIp())
                .triggeringDecoyId(event.triggeringDecoyId())
                .triggeringEndpoint(event.triggeringEndpoint())
                .score(event.score())
                .level(event.level())
                .reasons(new ArrayList<>(event.reasons()))
                .recentHitCount(event.recentHitCount())
                .distinctDecoyCount(event.distinctDecoyCount())
                .blocked(event.blocked())
                .blockExpiresAt(event.blockExpiresAt())
                .status(IncidentStatus.OPEN)
                .lastAssessmentId(event.assessmentId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        incident = incidentRepository.save(incident);

        addActivity(incident, "system", String.format(
                "Incident auto-created from a %s threat assessment (score=%d, sourceIp=%s).",
                event.level(), event.score(), event.sourceIp()));

        log.warn("Created incident id={} for sourceIp={} level={} score={}",
                incident.getId(), incident.getSourceIp(), incident.getLevel(), incident.getScore());

        return incident;
    }

    // Deliberately does NOT auto-reopen a CONTAINED incident just because
    // fresh traffic came in — it updates the numbers and leaves a note,
    // but the status change itself is left to a human analyst. Otherwise
    // the system would be fighting an analyst's own status decisions.
    private Incident updateFromAssessment(Incident incident, ThreatAssessmentEvent event) {
        boolean escalated = event.score() > incident.getScore();

        incident.setScore(event.score());
        incident.setLevel(event.level());
        incident.setReasons(new ArrayList<>(event.reasons()));
        incident.setRecentHitCount(event.recentHitCount());
        incident.setDistinctDecoyCount(event.distinctDecoyCount());
        incident.setBlocked(event.blocked());
        incident.setBlockExpiresAt(event.blockExpiresAt());
        incident.setTriggeringDecoyId(event.triggeringDecoyId());
        incident.setTriggeringEndpoint(event.triggeringEndpoint());
        incident.setLastAssessmentId(event.assessmentId());
        incident.setUpdatedAt(Instant.now());

        incident = incidentRepository.save(incident);

        addActivity(incident, "system", String.format(
                "New assessment merged into this open incident (score=%d, level=%s).%s",
                event.score(), event.level(), escalated ? " Threat has escalated." : ""));

        return incident;
    }

    // ---------- Analyst-facing operations ----------

    public List<Incident> findAll() {
        return incidentRepository.findAllByOrderByCreatedAtDesc();
    }

    public Incident findById(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident " + id + " not found"));
    }

    public List<IncidentActivity> findActivity(Long incidentId) {
        findById(incidentId); // 404s here if the incident doesn't exist
        return activityRepository.findAllByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    @Transactional
    public Incident addNote(Long id, String author, String message) {
        Incident incident = findById(id);
        addActivity(incident, author, message);
        return incident;
    }

    @Transactional
    public Incident assign(Long id, String analyst, Long expectedVersion, String actor) {
        Incident incident = findById(id);
        checkVersion(incident, expectedVersion);

        incident.setAssignedAnalyst(analyst);
        incident.setUpdatedAt(Instant.now());
        incident = flushOrConflict(incident);

        addActivity(incident, "system", "Assigned to " + analyst + " by " + actor + ".");
        broadcaster.incidentUpserted(incident);
        return incident;
    }

    @Transactional
    public Incident changeStatus(Long id, IncidentStatus newStatus, Long expectedVersion, String actor) {
        Incident incident = findById(id);
        checkVersion(incident, expectedVersion);

        List<IncidentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(incident.getStatus(), List.of());
        if (!allowed.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot move an incident from " + incident.getStatus() + " to " + newStatus);
        }

        IncidentStatus oldStatus = incident.getStatus();
        incident.setStatus(newStatus);
        incident.setUpdatedAt(Instant.now());
        incident = flushOrConflict(incident);

        addActivity(incident, actor, "Status changed from " + oldStatus + " to " + newStatus + ".");
        broadcaster.incidentUpserted(incident);
        return incident;
    }

    // This check catches the vast majority of real conflicts (the
    // request already carries a version that's out of date the moment it
    // arrives). It is intentionally paired with flushOrConflict() below,
    // which catches the rarer remaining race: two requests both pass this
    // check within the same instant, and only one can actually win the
    // database write.
    private void checkVersion(Incident incident, Long expectedVersion) {
        if (!incident.getVersion().equals(expectedVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Incident has changed since you loaded it (current version="
                            + incident.getVersion() + ", you had version=" + expectedVersion
                            + "). Reload and try again.");
        }
    }

    // saveAndFlush (not plain save) is the important detail here: it
    // forces Hibernate to actually run the UPDATE right now, on this
    // line, instead of deferring it to the end of the transaction. That
    // makes a real @Version conflict throw synchronously, right where we
    // can catch it — a plain save() could let the exception surface much
    // later, outside this try/catch entirely.
    private Incident flushOrConflict(Incident incident) {
        try {
            return incidentRepository.saveAndFlush(incident);
        } catch (ObjectOptimisticLockingFailureException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Incident was updated concurrently. Reload and try again.");
        }
    }

    private void addActivity(Incident incident, String author, String message) {
        IncidentActivity activity = IncidentActivity.builder()
                .incident(incident)
                .author(author)
                .message(message)
                .createdAt(Instant.now())
                .build();
        activityRepository.save(activity);
    }
}
