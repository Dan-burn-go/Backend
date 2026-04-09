package com.danburn.congestion.dto.response;

import java.util.List;

/**
 * 혼잡도 추이 응답 DTO (시간별·요일별 공용)
 */
public record CongestionTrendResponse(
        String areaCode,
        String areaName,
        List<TrendData> data
) {
    public record TrendData(
            int key,
            String label,
            String congestionLevel,
            double avgMinPeople,
            double avgMaxPeople,
            long dataCount
    ) {}
}
