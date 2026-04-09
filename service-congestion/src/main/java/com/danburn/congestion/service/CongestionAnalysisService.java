package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionRankingResponse;
import com.danburn.congestion.dto.response.CongestionRankingResponse.RankingEntry;
import com.danburn.congestion.dto.response.CongestionTrendResponse;
import com.danburn.congestion.dto.response.CongestionTrendResponse.TrendData;
import com.danburn.congestion.infra.SeoulArea;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionAnalysisService {

    private static final Map<Integer, String> DAY_NAMES = Map.of(
            1, "일요일",
            2, "월요일",
            3, "화요일",
            4, "수요일",
            5, "목요일",
            6, "금요일",
            7, "토요일"
    );

    private static final Map<String, Integer> LEVEL_ORDER = Map.of(
            "RELAXED", 0,
            "NORMAL", 1,
            "SLIGHTLY_CROWDED", 2,
            "BUSY", 3
    );

    private final CongestionJpaRepository congestionJpaRepository;
    private final CongestionRedisRepository congestionRedisRepository;

    /**
     * 시간별 혼잡도 추이 조회
     */
    public CongestionTrendResponse getHourlyTrend(String areaCode, int days) {
        validateAreaCode(areaCode);
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = congestionJpaRepository.findHourlyTrend(areaCode, since);

        List<TrendData> data = aggregateRows(rows, key -> key + "시");
        return new CongestionTrendResponse(areaCode, findAreaName(areaCode), data);
    }

    /**
     * 요일별 혼잡도 추이 조회
     */
    public CongestionTrendResponse getDailyTrend(String areaCode, int days) {
        validateAreaCode(areaCode);
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = congestionJpaRepository.findDailyTrend(areaCode, since);

        List<TrendData> data = aggregateRows(rows, key -> DAY_NAMES.getOrDefault(key, ""));
        return new CongestionTrendResponse(areaCode, findAreaName(areaCode), data);
    }

    /**
     * 실시간 혼잡 랭킹 (가장 붐비는 순)
     */
    public CongestionRankingResponse getBusiestRanking(int limit) {
        return buildRanking("BUSIEST", limit, Comparator.comparingInt(this::getLevelScore).reversed()
                .thenComparing((CongestionRedisDto dto) ->
                        dto.maxPeopleCount() != null ? dto.maxPeopleCount() : 0, Comparator.reverseOrder()));
    }

    /**
     * 실시간 한적 랭킹 (가장 한적한 순)
     */
    public CongestionRankingResponse getRelaxedRanking(int limit) {
        return buildRanking("RELAXED", limit, Comparator.comparingInt(this::getLevelScore)
                .thenComparing((CongestionRedisDto dto) ->
                        dto.minPeopleCount() != null ? dto.minPeopleCount() : Integer.MAX_VALUE));
    }

    private List<TrendData> aggregateRows(List<Object[]> rows,
                                           java.util.function.IntFunction<String> labelMapper) {
        Map<Integer, List<Object[]>> grouped = rows.stream()
                .collect(Collectors.groupingBy(r -> ((Number) r[0]).intValue(),
                        TreeMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    int key = entry.getKey();
                    List<Object[]> group = entry.getValue();

                    String dominantLevel = group.stream()
                            .max(Comparator.comparingLong(r -> ((Number) r[4]).longValue()))
                            .map(r -> (String) r[1])
                            .orElse("NORMAL");

                    double avgMin = group.stream()
                            .mapToDouble(r -> ((Number) r[2]).doubleValue())
                            .average().orElse(0);
                    double avgMax = group.stream()
                            .mapToDouble(r -> ((Number) r[3]).doubleValue())
                            .average().orElse(0);
                    long totalCount = group.stream()
                            .mapToLong(r -> ((Number) r[4]).longValue())
                            .sum();

                    return new TrendData(key, labelMapper.apply(key),
                            toLevelDescription(dominantLevel),
                            Math.round(avgMin * 10) / 10.0,
                            Math.round(avgMax * 10) / 10.0,
                            totalCount);
                })
                .toList();
    }

    private CongestionRankingResponse buildRanking(String type, int limit,
                                                    Comparator<CongestionRedisDto> comparator) {
        List<CongestionRedisDto> allData = congestionRedisRepository.findAll();
        if (allData.isEmpty()) {
            log.warn("Redis 캐시 비어있음 - 랭킹 조회 불가");
            return new CongestionRankingResponse(type, 0, Collections.emptyList());
        }

        AtomicInteger rankCounter = new AtomicInteger(1);
        List<RankingEntry> rankings = allData.stream()
                .sorted(comparator)
                .limit(limit)
                .map(dto -> new RankingEntry(
                        rankCounter.getAndIncrement(),
                        dto.areaCode(),
                        dto.areaName(),
                        dto.congestionLevel(),
                        dto.minPeopleCount(),
                        dto.maxPeopleCount(),
                        dto.populationTime()
                ))
                .toList();

        return new CongestionRankingResponse(type, rankings.size(), rankings);
    }

    private int getLevelScore(CongestionRedisDto dto) {
        if (dto.congestionLevel() == null) return -1;
        try {
            CongestionLevel level = CongestionLevel.fromDescription(dto.congestionLevel());
            return LEVEL_ORDER.getOrDefault(level.name(), -1);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private String toLevelDescription(String enumName) {
        try {
            return CongestionLevel.valueOf(enumName).getDescription();
        } catch (IllegalArgumentException e) {
            return enumName;
        }
    }

    private String findAreaName(String areaCode) {
        return SeoulArea.all().stream()
                .filter(a -> a.getCode().equals(areaCode))
                .findFirst()
                .map(SeoulArea::getName)
                .orElse(areaCode);
    }

    private void validateAreaCode(String areaCode) {
        boolean exists = SeoulArea.all().stream()
                .anyMatch(a -> a.getCode().equals(areaCode));
        if (!exists) {
            throw new GlobalException(400, "유효하지 않은 장소 코드입니다. areaCode: " + areaCode);
        }
    }
}
