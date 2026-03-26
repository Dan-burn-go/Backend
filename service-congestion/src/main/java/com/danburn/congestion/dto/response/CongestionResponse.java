package com.danburn.domain.congestion.dto.response;

/**
 * 혼잡도 분석 서비스에서 프론트에 내려주는 응답 DTO
 */
public record CongestionResponse(
        Long locationId,
        String locationName,
        String congestionLevel,
        Integer minPeopleCount,
        Integer maxPeopleCount,
        String populationTrend
) {}
