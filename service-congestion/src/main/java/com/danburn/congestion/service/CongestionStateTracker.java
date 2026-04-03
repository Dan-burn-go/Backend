package com.danburn.congestion.service;

import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionStateTracker {

    private final CongestionRedisRepository congestionRedisRepository;
    private final CongestionJpaRepository congestionJpaRepository;

    /**
     * BUSY로의 상승 엣지만 감지한다.
     * 기존 congestion:{areaCode} 데이터에서 이전 레벨을 조회하여 비교.
     * Redis 미스 시 DB 폴백.
     */
    public boolean shouldRequestAnalysis(String areaCode, CongestionLevel current) {
        if (current != CongestionLevel.BUSY) {
            return false;
        }

        CongestionLevel previous = getPreviousLevel(areaCode);
        if (previous == null) {
            log.info("[StateTracker] 최초 감지 - areaCode={}, level=BUSY", areaCode);
            return true;
        }

        boolean wasBusy = previous == CongestionLevel.BUSY;
        if (!wasBusy) {
            log.info("[StateTracker] 상승 엣지 감지 - areaCode={}, {} → BUSY", areaCode, previous);
        }
        return !wasBusy;
    }

    private CongestionLevel getPreviousLevel(String areaCode) {
        return congestionRedisRepository.findByAreaCode(areaCode)
                .map(dto -> CongestionLevel.fromDescription(dto.congestionLevel()))
                .orElseGet(() -> congestionJpaRepository
                        .findTopByAreaCodeOrderByCreatedAtDesc(areaCode)
                        .map(entity -> entity.getCongestionLevel())
                        .orElse(null));
    }
}
