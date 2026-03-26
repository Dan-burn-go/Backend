package com.danburn.congestion.dto.response;

/**
 * 공공 API에서 오는 데이터를 담는 DTO
 */
public record CongestionApiResponse(
        String areaName,
        String congestionLevel,
        Integer minPeopleCount,
        Integer maxPeopleCount,
        String populationTrend
) {}
