package com.danburn.domain.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.domain.congestion.domain.Congestion;
import com.danburn.domain.congestion.dto.CongestionRedisDto;
import com.danburn.domain.congestion.dto.response.CongestionResponse;
import com.danburn.domain.congestion.repository.CongestionJpaRepository;
import com.danburn.domain.congestion.repository.CongestionRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CongestionService {

    private final CongestionRedisRepository congestionRedisRepository;
    private final CongestionJpaRepository congestionJpaRepository;

    /**
     * Redis + DB 동시 저장 (Scheduler에서 호출)
     */
    @Transactional
    public void save(CongestionRedisDto dto) {
        // Redis 저장
        congestionRedisRepository.save(dto);

        // DB 저장 (이력 누적)
        Congestion entity = Congestion.builder()
                .locationId(dto.locationId())
                .congestionLevel(dto.congestionLevel())
                .minPeopleCount(dto.minPeopleCount())
                .maxPeopleCount(dto.maxPeopleCount())
                .populationTrend(dto.populationTrend())
                .build();
        congestionJpaRepository.save(entity);
    }

    /**
     * 단건 조회: Redis 우선 → DB 폴백
     */
    public CongestionResponse findByLocationId(Long locationId) {
        // 1. Redis에서 조회
        return congestionRedisRepository.findByLocationId(locationId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    // 2. Redis에 없으면 DB에서 최신 1건 조회
                    log.warn("Redis 캐시 미스 - locationId: {}, DB 폴백 조회", locationId);
                    return congestionJpaRepository.findTopByLocationIdOrderByCreatedAtDesc(locationId)
                            .map(this::toResponse)
                            .orElseThrow(() -> new GlobalException(404,
                                    "해당 장소의 혼잡도 데이터가 없습니다. locationId: " + locationId));
                });
    }

    /**
     * 전체 조회: Redis 우선 → DB 폴백
     */
    public List<CongestionResponse> findAll() {
        // 1. Redis에서 전체 조회
        List<CongestionRedisDto> redisList = congestionRedisRepository.findAll();
        if (!redisList.isEmpty()) {
            return redisList.stream()
                    .map(this::toResponse)
                    .toList();
        }

        // 2. Redis가 비어있으면 DB에서 조회
        log.warn("Redis 캐시 전체 미스 - DB 폴백 조회");
        return congestionJpaRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private CongestionResponse toResponse(CongestionRedisDto dto) {
        return new CongestionResponse(
                dto.locationId(),
                dto.locationName(),
                dto.congestionLevel().getDescription(),
                dto.minPeopleCount(),
                dto.maxPeopleCount(),
                dto.populationTrend().getDescription()
        );
    }

    private CongestionResponse toResponse(Congestion entity) {
        return new CongestionResponse(
                entity.getLocationId(),
                null, // DB에는 locationName이 없음 — 추후 locations 테이블 JOIN으로 개선
                entity.getCongestionLevel().getDescription(),
                entity.getMinPeopleCount(),
                entity.getMaxPeopleCount(),
                entity.getPopulationTrend().getDescription()
        );
    }
}
