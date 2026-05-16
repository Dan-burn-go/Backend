package com.danburn.congestion.event;

/**
 * BUSY 구간 중 current/avg 비율 임계 초과 시 RabbitMQ 로 발행되는 anomaly 이벤트
 */
public record CongestionAnomalyEvent(
        String areaName,
        String areaCode,
        String congestionLevel,
        Integer maxPeopleCount,
        Double avgMaxPeople,
        Double ratio,
        String populationTime
) {}
