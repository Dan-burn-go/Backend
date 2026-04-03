package com.danburn.congestion.event;

/**
 * BUSY 상승 엣지 감지 시 RabbitMQ로 발행되는 이벤트
 */
public record CongestionBusyEvent(
        String areaCode,
        String congestionLevel,
        Integer maxPeopleCount,
        String populationTime
) {}
