package com.danburn.congestion.event;

import com.danburn.congestion.config.rabbitmq.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Publisher Confirm 으로 브로커 수신을 확인하고, 실패(nack·타임아웃·반송·연결 실패) 이벤트는
 * 대기열에 보관했다가 다음 수집 주기에 재발행한다. 상승 엣지에서만 발행하므로 유실되면 다시 감지되지 않기 때문.
 */
@Slf4j
@Component
public class CongestionEventPublisher {

    private static final Duration DEFAULT_CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;
    // ponytail: 메모리 보관이라 브로커 장애 중 재시작 시 대기 이벤트 유실, 막아야 하면 Transactional Outbox
    private final Queue<PendingEvent> pending = new ConcurrentLinkedQueue<>();

    @Autowired
    public CongestionEventPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, DEFAULT_CONFIRM_TIMEOUT);
    }

    CongestionEventPublisher(RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    public void publishBusyEvent(CongestionBusyEvent event) {
        boolean sent = publishOrQueue(new PendingEvent(RabbitMqConfig.BUSY_ROUTING_KEY, event));
        log.info("[EventPublisher] BUSY 이벤트 {} - areaCode={}, time={}",
                sent ? "발행" : "재시도 대기", event.areaCode(), event.populationTime());
    }

    public void publishAnomalyEvent(CongestionAnomalyEvent event) {
        boolean sent = publishOrQueue(new PendingEvent(RabbitMqConfig.ANOMALY_ROUTING_KEY, event));
        log.info("[EventPublisher] ANOMALY 이벤트 {} - areaCode={}, current={}, avg={}, ratio={}, time={}",
                sent ? "발행" : "재시도 대기", event.areaCode(), event.maxPeopleCount(), event.avgMaxPeople(),
                event.ratio(), event.populationTime());
    }

    // 대기 이벤트를 순서대로 재발행. 하나라도 실패하면 브로커가 아직 장애라 보고 중단 (순서 유지, 주기 지연 최소화)
    public void republishPending() {
        if (pending.isEmpty()) {
            return;
        }
        log.info("[EventPublisher] 대기 이벤트 재발행 시도 - {}건", pending.size());
        PendingEvent event;
        while ((event = pending.peek()) != null) {
            if (!send(event)) {
                log.warn("[EventPublisher] 재발행 중단, 다음 주기 재시도 - 남은 {}건", pending.size());
                return;
            }
            pending.poll();
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    private boolean publishOrQueue(PendingEvent event) {
        if (send(event)) {
            return true;
        }
        pending.add(event);
        return false;
    }

    // 발행 후 confirm 대기. ack 이고 반송이 없을 때만 성공
    private boolean send(PendingEvent event) {
        CorrelationData correlation = new CorrelationData();
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, event.routingKey(), event.payload(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (confirm.isAck() && correlation.getReturned() == null) {
                return true;
            }
            log.warn("[EventPublisher] 발행 미확인 - routingKey={}, ack={}, reason={}, returned={}",
                    event.routingKey(), confirm.isAck(), confirm.getReason(), correlation.getReturned() != null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[EventPublisher] confirm 대기 중단 - routingKey={}", event.routingKey());
        } catch (Exception e) {
            log.warn("[EventPublisher] 발행 실패 - routingKey={}, {}: {}",
                    event.routingKey(), e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }

    private record PendingEvent(String routingKey, Object payload) {
    }
}
