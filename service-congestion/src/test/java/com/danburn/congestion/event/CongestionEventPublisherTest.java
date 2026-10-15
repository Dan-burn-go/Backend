package com.danburn.congestion.event;

import com.danburn.congestion.config.rabbitmq.RabbitMqConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CongestionEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private CongestionEventPublisher publisher;

    private final CongestionBusyEvent busy = new CongestionBusyEvent(
            "명동", "POI001", "붐빔", 34000, "2026-04-01 14:00"
    );

    @BeforeEach
    void setUp() {
        publisher = new CongestionEventPublisher(rabbitTemplate, Duration.ofMillis(100));
    }

    // 브로커 응답(ack/nack) 흉내
    private void brokerReplies(boolean ack) {
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class), any(CorrelationData.class));
    }

    @Nested
    @DisplayName("publishBusyEvent")
    class PublishBusyEvent {

        @Test
        @DisplayName("ack → 올바른 exchange/routingKey 로 발행, 재시도 대기 없음")
        void publishBusy() {
            brokerReplies(true);

            publisher.publishBusyEvent(busy);

            then(rabbitTemplate).should().convertAndSend(
                    eq(RabbitMqConfig.EXCHANGE_NAME), eq(RabbitMqConfig.BUSY_ROUTING_KEY),
                    eq(busy), any(CorrelationData.class));
            assertThat(publisher.pendingCount()).isZero();
        }

        @Test
        @DisplayName("nack → 재시도 대기열에 보관")
        void nack() {
            brokerReplies(false);

            publisher.publishBusyEvent(busy);

            assertThat(publisher.pendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("confirm 타임아웃 → 재시도 대기열에 보관")
        void timeout() {
            publisher.publishBusyEvent(busy);   // mock 은 confirm 을 완료하지 않음

            assertThat(publisher.pendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("브로커 연결 실패 → 예외 없이 재시도 대기열에 보관")
        void connectionFailure() {
            doThrow(new AmqpConnectException(new ConnectException("down")))
                    .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class), any(CorrelationData.class));

            publisher.publishBusyEvent(busy);

            assertThat(publisher.pendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("라우팅 불가로 반송 → 재시도 대기열에 보관")
        void returned() {
            doAnswer(inv -> {
                CorrelationData cd = inv.getArgument(3);
                cd.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", RabbitMqConfig.EXCHANGE_NAME, RabbitMqConfig.BUSY_ROUTING_KEY));
                cd.getFuture().complete(new CorrelationData.Confirm(true, null));
                return null;
            }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class), any(CorrelationData.class));

            publisher.publishBusyEvent(busy);

            assertThat(publisher.pendingCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("publishAnomalyEvent")
    class PublishAnomalyEvent {

        @Test
        @DisplayName("ANOMALY 이벤트 → 올바른 exchange/routingKey로 발행")
        void publishAnomaly() {
            brokerReplies(true);
            CongestionAnomalyEvent event = new CongestionAnomalyEvent(
                    "명동", "POI001", "붐빔", 34000, 20000.0, 1.7, "2026-04-01 14:00"
            );

            publisher.publishAnomalyEvent(event);

            then(rabbitTemplate).should().convertAndSend(
                    eq(RabbitMqConfig.EXCHANGE_NAME), eq(RabbitMqConfig.ANOMALY_ROUTING_KEY),
                    eq(event), any(CorrelationData.class));
            assertThat(publisher.pendingCount()).isZero();
        }
    }

    @Nested
    @DisplayName("republishPending")
    class RepublishPending {

        @Test
        @DisplayName("브로커 복구 후 → 대기 이벤트 재발행, 대기열 비움")
        void recovered() {
            brokerReplies(false);
            publisher.publishBusyEvent(busy);
            brokerReplies(true);

            publisher.republishPending();

            then(rabbitTemplate).should(times(2)).convertAndSend(
                    eq(RabbitMqConfig.EXCHANGE_NAME), eq(RabbitMqConfig.BUSY_ROUTING_KEY),
                    eq(busy), any(CorrelationData.class));
            assertThat(publisher.pendingCount()).isZero();
        }

        @Test
        @DisplayName("재발행 중 첫 실패에서 중단 → 남은 이벤트 순서 유지")
        void stopsAtFirstFailure() {
            CongestionBusyEvent second = new CongestionBusyEvent("홍대", "POI002", "붐빔", 1, "2026-04-01 14:00");
            brokerReplies(false);
            publisher.publishBusyEvent(busy);
            publisher.publishBusyEvent(second);

            publisher.republishPending();   // 첫 건 실패 → 두 번째는 시도 안 함

            then(rabbitTemplate).should(times(2)).convertAndSend(
                    eq(RabbitMqConfig.EXCHANGE_NAME), eq(RabbitMqConfig.BUSY_ROUTING_KEY),
                    eq(busy), any(CorrelationData.class));
            then(rabbitTemplate).should(times(1)).convertAndSend(
                    eq(RabbitMqConfig.EXCHANGE_NAME), eq(RabbitMqConfig.BUSY_ROUTING_KEY),
                    eq(second), any(CorrelationData.class));
            assertThat(publisher.pendingCount()).isEqualTo(2);

            brokerReplies(true);
            publisher.republishPending();

            assertThat(publisher.pendingCount()).isZero();
        }

        @Test
        @DisplayName("여전히 장애 → 대기열에 그대로 남음")
        void stillDown() {
            brokerReplies(false);
            publisher.publishBusyEvent(busy);

            publisher.republishPending();

            assertThat(publisher.pendingCount()).isEqualTo(1);
        }
    }
}
