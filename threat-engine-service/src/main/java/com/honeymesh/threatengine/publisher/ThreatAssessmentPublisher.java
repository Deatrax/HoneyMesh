package com.honeymesh.threatengine.publisher;

import com.honeymesh.threatengine.config.RabbitConfig;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ThreatAssessmentPublisher {

    private static final Logger log = LoggerFactory.getLogger(ThreatAssessmentPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ThreatAssessmentPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ThreatAssessmentEvent event) {
        if (event == null) {
            log.warn("Cannot publish null ThreatAssessmentEvent");
            return;
        }

        log.info("Publishing ThreatAssessmentEvent to exchange '{}' [routingKey='{}']: assessmentId={}, IP={}, score={}, level={}",
                RabbitConfig.EXCHANGE,
                RabbitConfig.ASSESSMENT_ROUTING_KEY,
                event.assessmentId(),
                event.sourceIp(),
                event.score(),
                event.level());

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ASSESSMENT_ROUTING_KEY,
                event
        );
    }
}
