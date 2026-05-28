package com.danburn.congestion.event;

import com.danburn.congestion.config.rabbitmq.RabbitMqConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CongestionEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CongestionEventPublisher publisher;

    @Nested
    @DisplayName("publishBusyEvent")
    class PublishBusyEvent {

        @Test
        @DisplayName("BUSY 이벤트 → 올바른 exchange/routingKey로 발행")
        void publishBusy() {
            CongestionBusyEvent event = new CongestionBusyEvent(
                    "명동", "POI001", "붐빔", 34000, "2026-04-01 14:00"
            );

            publisher.publishBusyEvent(event);

            then(rabbitTemplate).should().convertAndSend(
                    RabbitMqConfig.EXCHANGE_NAME,
                    RabbitMqConfig.BUSY_ROUTING_KEY,
                    event
            );
        }
    }

    @Nested
    @DisplayName("publishAnomalyEvent")
    class PublishAnomalyEvent {

        @Test
        @DisplayName("ANOMALY 이벤트 → 올바른 exchange/routingKey로 발행")
        void publishAnomaly() {
            CongestionAnomalyEvent event = new CongestionAnomalyEvent(
                    "명동", "POI001", "붐빔", 34000, 20000.0, 1.7, "2026-04-01 14:00"
            );

            publisher.publishAnomalyEvent(event);

            then(rabbitTemplate).should().convertAndSend(
                    RabbitMqConfig.EXCHANGE_NAME,
                    RabbitMqConfig.ANOMALY_ROUTING_KEY,
                    event
            );
        }
    }
}
