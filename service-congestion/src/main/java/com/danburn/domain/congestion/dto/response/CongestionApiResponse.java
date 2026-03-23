package com.danburn.domain.congestion.dto.response;

/*
 * 공공 API에서 오는 데이터를 담는 DTO
 */
public record CongestionApiResponse(
    String areaName,           // AREA_NM (장소명)
    String congestionLevel,    // AREA_CONGEST_LVL (여유/보통/약간 붐빔/붐빔)
    Integer minPeopleCount,    // AREA_PPLTN_MIN
    Integer maxPeopleCount,    // AREA_PPLTN_MAX
    String populationTrend     // PPLTN_RATE_OF_CHANGE (증가/감소/유지)
) {}