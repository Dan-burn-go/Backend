package com.danburn.congestion.config.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME = "congestion.events";
    public static final String BUSY_ROUTING_KEY = "congestion.busy";
    public static final String BUSY_QUEUE_NAME = "ai.congestion.analysis";
    public static final String AI_REPORT_ROUTING_KEY = "ai.report";
    public static final String AI_REPORT_QUEUE_NAME = "congestion.ai.report";

    private static final String BUSY_QUEUE_DLX = "ai.congestion.dlx";
    private static final String BUSY_QUEUE_DLX_ROUTING_KEY = BUSY_QUEUE_NAME;

    @Bean
    public TopicExchange congestionExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue congestionBusyQueue() {
        return QueueBuilder.durable(BUSY_QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", BUSY_QUEUE_DLX)
                .withArgument("x-dead-letter-routing-key", BUSY_QUEUE_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding congestionBusyBinding(Queue congestionBusyQueue, TopicExchange congestionExchange) {
        return BindingBuilder.bind(congestionBusyQueue)
                .to(congestionExchange)
                .with(BUSY_ROUTING_KEY);
    }

    @Bean
    public Queue aiReportQueue() {
        return new Queue(AI_REPORT_QUEUE_NAME, true);
    }

    @Bean
    public Binding aiReportBinding(Queue aiReportQueue, TopicExchange congestionExchange) {
        return BindingBuilder.bind(aiReportQueue)
                .to(congestionExchange)
                .with(AI_REPORT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
