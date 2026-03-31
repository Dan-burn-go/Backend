package com.danburn.congestion.dto;

import java.util.List;

/**
 * Redis에 저장되는 혼잡도 데이터 DTO
 */
public record CongestionRedisDto(
        String areaCode,
        String congestionLevel,
        String congestionMessage,
        Integer minPeopleCount,
        Integer maxPeopleCount,
        String populationTime,
        List<ForecastDto> forecasts
) {
    public record ForecastDto(
            String forecastTime,
            String congestionLevel,
            Integer minPeopleCount,
            Integer maxPeopleCount
    ) {}
}
