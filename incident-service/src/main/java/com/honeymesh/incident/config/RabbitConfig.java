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

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
