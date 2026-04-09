package com.danburn.congestion.dto.response;

import java.util.List;

/**
 * 혼잡도 랭킹 응답 DTO
 */
public record CongestionRankingResponse(
        String type,
        int totalCount,
        List<RankingEntry> rankings
) {
    public record RankingEntry(
            int rank,
            String areaCode,
            String areaName,
            String congestionLevel,
            Integer minPeopleCount,
            Integer maxPeopleCount,
            String populationTime
    ) {}
}
