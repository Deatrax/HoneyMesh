package com.honeymesh.threatengine.publisher;

import com.honeymesh.threatengine.config.RabbitConfig;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import com.honeymesh.threatengine.model.ThreatLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThreatAssessmentPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ThreatAssessmentPublisher publisher;

    @Test
    @DisplayName("Publishes ThreatAssessmentEvent to exchange honeymesh.events with routing key threat.assessment.created")
    void testPublishEvent() {
        ThreatAssessmentEvent event = new ThreatAssessmentEvent(
                UUID.randomUUID().toString(),
                "203.0.113.7",
                "decoy-1",
                "/api/test",
                85,
                ThreatLevel.CRITICAL,
                List.of("Critical risk"),
                5,
                3,
                true,
                Instant.now().plusSeconds(300),
                Instant.now()
        );

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ASSESSMENT_ROUTING_KEY,
                event
        );
    }
}
