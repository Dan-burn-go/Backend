package com.danburn.congestion.dto.response;

import java.util.List;

/**
 * 프론트에 내려주는 혼잡도 응답 DTO
 */
public record CongestionResponse(
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
