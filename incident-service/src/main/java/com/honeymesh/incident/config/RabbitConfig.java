package com.honeymesh.incident.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same exchange/queue/routing-key names Prince declared in Threat Engine's
 * RabbitConfig.java. RabbitMQ allows two services to declare the same
 * exchange/queue as long as the definitions match exactly (same name,
 * same durability) — it's not an error, it's just idempotent setup. This
 * is why Incident Service doesn't need Threat Engine to be "first" at
 * startup; whichever service starts first creates it, the other just
 * confirms it already matches.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "honeymesh.events";
    public static final String ASSESSMENT_QUEUE = "incident-service.threat-assessment-queue";
    public static final String ASSESSMENT_ROUTING_KEY = "threat.assessment.created";

    @Bean
    public TopicExchange honeymeshExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue threatAssessmentQueue() {
        return new Queue(ASSESSMENT_QUEUE, true);
    }

    @Bean
    public Binding threatAssessmentBinding(Queue threatAssessmentQueue, TopicExchange honeymeshExchange) {
        return BindingBuilder.bind(threatAssessmentQueue).to(honeymeshExchange).with(ASSESSMENT_ROUTING_KEY);
    }

    /**
     * Same INFERRED fix Prince used in Threat Engine's RabbitConfig, for
     * the same reason: without it, this converter trusts the producer's
     * __TypeId__ header, which names Threat Engine's own class
     * (com.honeymesh.threatengine.event.ThreatAssessmentEvent) — a class
     * that doesn't exist on THIS service's classpath. INFERRED tells it
     * instead to deserialize into whatever type our own
     * @RabbitListener method declares as its parameter.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
