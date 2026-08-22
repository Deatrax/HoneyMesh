package com.honeymesh.threatengine.config;

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
    public static final String TELEMETRY_QUEUE = "threat-engine.telemetry-queue";
    public static final String TELEMETRY_ROUTING_KEY = "decoy.telemetry.recorded";

    @Bean
    public TopicExchange honeymeshExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue telemetryQueue() {
        return new Queue(TELEMETRY_QUEUE, true);
    }

    @Bean
    public Binding telemetryBinding(Queue telemetryQueue, TopicExchange honeymeshExchange) {
        return BindingBuilder.bind(telemetryQueue).to(honeymeshExchange).with(TELEMETRY_ROUTING_KEY);
    }

    /**
     * TypePrecedence.INFERRED is the important line in this whole file.
     * Without it, this converter trusts the producer's __TypeId__ header, which
     * names decoy-service's class (com.honeymesh.decoy.event.TelemetryEvent) —
     * a class that doesn't exist in THIS service's classpath. INFERRED tells it
     * to trust the @RabbitListener method's declared parameter type instead.
     * This is the standard fix for cross-service JSON messaging with Jackson2JsonMessageConverter.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
