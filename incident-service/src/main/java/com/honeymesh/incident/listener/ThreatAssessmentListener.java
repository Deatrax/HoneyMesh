package com.honeymesh.incident.listener;

import com.honeymesh.incident.config.RabbitConfig;
import com.honeymesh.incident.event.ThreatAssessmentEvent;
import com.honeymesh.incident.service.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ThreatAssessmentListener {

    private static final Logger log = LoggerFactory.getLogger(ThreatAssessmentListener.class);

    private final IncidentService incidentService;

    public ThreatAssessmentListener(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    // RabbitConfig.ASSESSMENT_QUEUE is a "public static final String" —
    // a compile-time constant — so it's legal to reference it directly
    // inside an annotation like this, instead of retyping the literal
    // string and risking a typo.
    @RabbitListener(queues = RabbitConfig.ASSESSMENT_QUEUE)
    public void onThreatAssessment(ThreatAssessmentEvent event) {
        if (event == null) {
            return;
        }
        log.info("Received ThreatAssessmentEvent id={} sourceIp={} level={} score={}",
                event.assessmentId(), event.sourceIp(), event.level(), event.score());

        // All the actual decision-making (dedupe, threshold, create vs.
        // merge) lives in IncidentService — this class's only job is
        // "message arrived, hand it off".
        incidentService.handleAssessment(event);
    }
}
