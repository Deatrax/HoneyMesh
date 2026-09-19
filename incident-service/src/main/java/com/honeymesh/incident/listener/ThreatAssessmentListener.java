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

    @RabbitListener(queues = RabbitConfig.ASSESSMENT_QUEUE)
    public void onThreatAssessment(ThreatAssessmentEvent event) {
        if (event == null) {
            return;
        }
        log.info("Received ThreatAssessmentEvent id={} sourceIp={} level={} score={}",
                event.assessmentId(), event.sourceIp(), event.level(), event.score());

        incidentService.handleAssessment(event);
    }
}
