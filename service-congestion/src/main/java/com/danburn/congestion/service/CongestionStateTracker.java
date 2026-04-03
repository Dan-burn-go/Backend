package com.danburn.congestion.service;

import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionStateTracker {

    private final CongestionRedisRepository congestionRedisRepository;
    private final CongestionJpaRepository congestionJpaRepository;

    /**
     * BUSY 지역 중 상승 엣지(non-BUSY → BUSY)가 발생한 areaCode 목록을 반환한다.
     * 이전 상태를 벌크로 조회하여 N+1 문제를 방지한다.
     */
    public List<String> filterAreaCodesForAnalysis(List<CongestionRedisDto> dtos) {
        List<String> busyAreaCodes = dtos.stream()
                .filter(dto -> CongestionLevel.fromDescription(dto.congestionLevel()) == CongestionLevel.BUSY)
                .map(CongestionRedisDto::areaCode)
                .toList();

        if (busyAreaCodes.isEmpty()) {
            return List.of();
        }

        Map<String, CongestionLevel> previousLevels = getPreviousLevels(busyAreaCodes);

        List<String> result = new ArrayList<>();
        for (String areaCode : busyAreaCodes) {
            CongestionLevel previous = previousLevels.get(areaCode);
            if (previous == null) {
                log.info("[StateTracker] 최초 감지 - areaCode={}, level=BUSY", areaCode);
                result.add(areaCode);
            } else if (previous != CongestionLevel.BUSY) {
                log.info("[StateTracker] 상승 엣지 감지 - areaCode={}, {} → BUSY", areaCode, previous);
                result.add(areaCode);
            }
        }
        return result;
    }

    private Map<String, CongestionLevel> getPreviousLevels(List<String> areaCodes) {
        Map<String, CongestionLevel> result = new HashMap<>();
        List<String> missingCodes = new ArrayList<>();

        // Redis 벌크 조회 (multiGet, 1회 네트워크 호출)
        List<CongestionRedisDto> redisDtos = congestionRedisRepository.findAllByAreaCodes(areaCodes);
        for (int i = 0; i < areaCodes.size(); i++) {
            CongestionRedisDto dto = redisDtos.get(i);
            if (dto != null) {
                result.put(areaCodes.get(i), CongestionLevel.fromDescription(dto.congestionLevel()));
            } else {
                missingCodes.add(areaCodes.get(i));
            }
        }

        // Redis 미스 시 DB 벌크 폴백 (1회 쿼리)
        if (!missingCodes.isEmpty()) {
            congestionJpaRepository.findLatestByAreaCodes(missingCodes)
                    .forEach(entity -> result.put(entity.getAreaCode(), entity.getCongestionLevel()));
        }

        return result;
    }
}
