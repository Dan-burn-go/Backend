package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.Congestion;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionResponse;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CongestionService {

    private final CongestionRedisRepository congestionRedisRepository;
    private final CongestionJpaRepository congestionJpaRepository;

    /**
     * Redis에 배치 저장 (동기 — 프론트 서빙의 핵심 경로)
     */
    public void saveAllToRedis(List<CongestionRedisDto> dtos) {
        congestionRedisRepository.saveAll(dtos);
    }

    /**
     * DB에 배치 저장 (비동기 — 이력 보관용, 실패해도 Redis에 영향 없음)
     */
    @Async
    @Transactional
    public void saveAllToDb(List<CongestionRedisDto> dtos) {
        List<Congestion> entities = dtos.stream()
                .map(dto -> Congestion.builder()
                        .locationId(dto.locationId())
                        .congestionLevel(dto.congestionLevel())
                        .minPeopleCount(dto.minPeopleCount())
                        .maxPeopleCount(dto.maxPeopleCount())
                        .populationTrend(dto.populationTrend())
                        .build())
                .toList();
        congestionJpaRepository.saveAll(entities);
        log.debug("[CongestionService] DB 이력 저장 완료 — {}건", entities.size());
    }

    /**
     * 오래된 데이터 삭제 (DataCleanupScheduler에서 호출)
     */
    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        return congestionJpaRepository.deleteByCreatedAtBefore(cutoff);
    }

    public CongestionResponse findByLocationId(Long locationId) {
        return congestionRedisRepository.findByLocationId(locationId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    log.warn("Redis 캐시 미스 - locationId: {}, DB 폴백 조회", locationId);
                    return congestionJpaRepository.findTopByLocationIdOrderByCreatedAtDesc(locationId)
                            .map(this::toResponse)
                            .orElseThrow(() -> new GlobalException(404,
                                    "해당 장소의 혼잡도 데이터가 없습니다. locationId: " + locationId));
                });
    }

    public List<CongestionResponse> findAll() {
        List<CongestionRedisDto> redisList = congestionRedisRepository.findAll();
        if (!redisList.isEmpty()) {
            return redisList.stream()
                    .map(this::toResponse)
                    .toList();
        }

        log.warn("Redis 캐시 전체 미스 - DB 폴백 조회");
        return congestionJpaRepository.findLatestPerLocation().stream()
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
                null,
                entity.getCongestionLevel().getDescription(),
                entity.getMinPeopleCount(),
                entity.getMaxPeopleCount(),
                entity.getPopulationTrend().getDescription()
        );
    }
}
