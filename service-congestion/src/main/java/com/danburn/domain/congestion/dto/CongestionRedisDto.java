package com.danburn.domain.congestion.dto;

import com.danburn.domain.congestion.domain.CongestionLevel;
import com.danburn.domain.congestion.domain.PopulationTrend;
import java.time.Instant;

/**
 * Redis에 저장되는 데이터를 담는 DTO
 */
public record CongestionRedisDto(
        Long locationId,
        CongestionLevel congestionLevel,
        Integer minPeopleCount,
        Integer maxPeopleCount,
        PopulationTrend populationTrend,
        Instant createdAt
) {}
