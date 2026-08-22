package com.honeymesh.decoy.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology is declared on the producer side here (and duplicated in
 * threat-engine-service). Declarations are idempotent in RabbitMQ, so having
 * both services declare the same exchange/queue/binding is harmless and avoids
 * "queue doesn't exist yet" races depending on which service starts first.
 *
 * NOTE for Day 2: this is a *durable* queue with no dead-letter-exchange yet.
 * Retry/DLQ wiring is intentionally deferred — see README.
 */
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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
