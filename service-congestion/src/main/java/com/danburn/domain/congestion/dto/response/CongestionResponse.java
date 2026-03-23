package com.danburn.domain.congestion.dto.response;

import com.danburn.domain.congestion.domain.CongestionLevel;
import com.danburn.domain.congestion.domain.PopulationTrend;

/*
 * 혼잡도 분석 서비스에서 오는 데이터를 담는 DTO
 */
  public record CongestionResponse(
      String areaName,
      CongestionLevel congestionLevel,    // "여유" (한글로)
      Integer minPeopleCount,
      Integer maxPeopleCount,
      PopulationTrend populationTrend
  ) {}