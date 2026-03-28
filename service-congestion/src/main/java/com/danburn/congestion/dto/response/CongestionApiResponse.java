package com.danburn.congestion.dto.response;

import java.util.List;

/**
 * 서울시 실시간 도시데이터 공공 API 응답 DTO
 */
public record CongestionApiResponse(
        String areaName,
        String areaCode,
        String congestionLevel,
        String congestionMessage,
        Integer minPeopleCount,
        Integer maxPeopleCount,
        String populationTime,
        List<ForecastResponse> forecasts
) {
    public record ForecastResponse(
            String forecastTime,
            String congestionLevel,
            Integer minPeopleCount,
            Integer maxPeopleCount
    ) {}
}
