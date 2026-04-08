package com.danburn.congestion.event;

import com.danburn.congestion.config.rabbitmq.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishBusyEvent(CongestionBusyEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.BUSY_ROUTING_KEY,
                event
        );
        log.info("[EventPublisher] BUSY 이벤트 발행 - areaCode={}, time={}",
                event.areaCode(), event.populationTime());
    }
}
