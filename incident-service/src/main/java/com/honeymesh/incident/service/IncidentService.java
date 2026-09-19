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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private static final String BLOCK_KEY_PREFIX = "honeymesh:block:";

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
    private final StringRedisTemplate redisTemplate;

    private final ThreatLevel minLevelForIncident;

    public IncidentService(IncidentRepository incidentRepository,
                            IncidentActivityRepository activityRepository,
                            ProcessedAssessmentRepository processedAssessmentRepository,
                            IncidentBroadcaster broadcaster,
                            StringRedisTemplate redisTemplate,
                            @Value("${incident.auto-create.min-level:HIGH}") ThreatLevel minLevelForIncident) {
        this.incidentRepository = incidentRepository;
        this.activityRepository = activityRepository;
        this.processedAssessmentRepository = processedAssessmentRepository;
        this.broadcaster = broadcaster;
        this.redisTemplate = redisTemplate;
        this.minLevelForIncident = minLevelForIncident;
    }

    @Transactional
    public void handleAssessment(ThreatAssessmentEvent event) {
        if (!claimAssessment(event.assessmentId())) {
            log.info("Duplicate ThreatAssessmentEvent id={} — already processed, skipping", event.assessmentId());
            return;
        }

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

    public List<Incident> findAll() {
        return incidentRepository.findAllByOrderByCreatedAtDesc();
    }

    public Incident findById(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident " + id + " not found"));
    }

    public List<IncidentActivity> findActivity(Long incidentId) {
        findById(incidentId);
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

    @Transactional
    public Incident unblock(Long id, Long expectedVersion, String actor) {
        Incident incident = findById(id);
        checkVersion(incident, expectedVersion);

        redisTemplate.delete(BLOCK_KEY_PREFIX + incident.getSourceIp());

        incident.setBlocked(false);
        incident.setUpdatedAt(Instant.now());
        incident = flushOrConflict(incident);

        addActivity(incident, actor, "Manually unblocked " + incident.getSourceIp() + " (overriding auto-expiry).");
        broadcaster.incidentUpserted(incident);
        return incident;
    }

    @Transactional
    public Incident permaBlock(Long id, Long expectedVersion, String actor) {
        Incident incident = findById(id);
        checkVersion(incident, expectedVersion);

        redisTemplate.opsForValue().set(BLOCK_KEY_PREFIX + incident.getSourceIp(), "true");

        incident.setBlocked(true);
        incident.setBlockExpiresAt(null);
        incident.setUpdatedAt(Instant.now());
        incident = flushOrConflict(incident);

        addActivity(incident, actor, "Permanently banned " + incident.getSourceIp() + " (no auto-expiry).");
        broadcaster.incidentUpserted(incident);
        return incident;
    }

    private void checkVersion(Incident incident, Long expectedVersion) {
        if (!incident.getVersion().equals(expectedVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Incident has changed since you loaded it (current version="
                            + incident.getVersion() + ", you had version=" + expectedVersion
                            + "). Reload and try again.");
        }
    }

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
