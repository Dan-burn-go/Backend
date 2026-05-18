package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.Congestion;
import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionResponse;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class CongestionService {

    private static final DateTimeFormatter POPULATION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CongestionRedisRepository congestionRedisRepository;
    private final CongestionJpaRepository congestionJpaRepository;
    private final ObjectMapper objectMapper;

    // findAll DB 폴백 single-flight 가드 (Redis 미스 폭주 시 DB 호출 1회로 압축)
    private final ReentrantLock dbFallbackLock = new ReentrantLock();
    private static final long DB_FALLBACK_LOCK_WAIT_MS = 300;

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
                .map(dto -> {
                    String forecastJson;
                    try {
                        forecastJson = objectMapper.writeValueAsString(dto.forecasts());
                    } catch (JsonProcessingException e) {
                        log.warn("[CongestionService] forecast JSON 변환 실패 - areaCode={}", dto.areaCode(), e);
                        forecastJson = "[]";
                    }
                    return Congestion.builder()
                            .areaCode(dto.areaCode())
                            .congestionLevel(CongestionLevel.fromDescription(dto.congestionLevel()))
                            .congestionMessage(dto.congestionMessage())
                            .minPeopleCount(dto.minPeopleCount())
                            .maxPeopleCount(dto.maxPeopleCount())
                            .populationTime(parsePopulationTime(dto.populationTime()))
                            .forecast(forecastJson)
                            .build();
                })
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

    public CongestionResponse findByAreaCode(String areaCode) {
        return congestionRedisRepository.findByAreaCode(areaCode)
                .map(this::toResponse)
                .orElseGet(() -> {
                    log.warn("Redis 캐시 미스 - areaCode: {}, DB 폴백 조회", areaCode);
                    return congestionJpaRepository.findTopByAreaCodeOrderByCreatedAtDesc(areaCode)
                            .map(this::toResponse)
                            .orElseThrow(() -> new GlobalException(404,
                                    "해당 장소의 혼잡도 데이터가 없습니다. areaCode: " + areaCode));
                });
    }

    public List<CongestionResponse> findAll() {
        List<CongestionRedisDto> redisList = congestionRedisRepository.findAll();
        if (!redisList.isEmpty()) {
            return redisList.stream()
                    .map(this::toResponse)
                    .toList();
        }
        return findAllFromDbWithSingleFlight();
    }

    /**
     * Redis 전체 미스 시 DB 폴백을 직렬화하고 결과를 Redis에 백필.
     * 락을 못 잡은 요청은 Redis 재조회로 대체 — 동시 트래픽이 폭주해도
     * DB 호출은 최대 1회로 압축되어 Hikari 풀 고갈을 막는다.
     */
    private List<CongestionResponse> findAllFromDbWithSingleFlight() {
        boolean acquired = false;
        try {
            acquired = dbFallbackLock.tryLock(DB_FALLBACK_LOCK_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!acquired) {
            // 다른 스레드가 백필을 마쳤을 수 있으니 Redis 한 번 더 조회
            List<CongestionRedisDto> retry = congestionRedisRepository.findAll();
            if (!retry.isEmpty()) {
                return retry.stream().map(this::toResponse).toList();
            }
            return Collections.emptyList();
        }

        try {
            // double-check: 락 대기 중 다른 스레드가 백필했을 수 있음
            List<CongestionRedisDto> cached = congestionRedisRepository.findAll();
            if (!cached.isEmpty()) {
                return cached.stream().map(this::toResponse).toList();
            }

            log.warn("Redis 캐시 전체 미스 - DB 폴백 조회");
            List<Congestion> entities = congestionJpaRepository.findLatestPerLocation();
            if (!entities.isEmpty()) {
                try {
                    congestionRedisRepository.saveAll(entities.stream()
                            .map(this::toRedisDto)
                            .toList());
                } catch (Exception e) {
                    log.warn("[CongestionService] DB 폴백 결과 Redis 백필 실패", e);
                }
            }
            return entities.stream().map(this::toResponse).toList();
        } finally {
            dbFallbackLock.unlock();
        }
    }

    // 백필 전용 변환. areaName은 엔티티에 없어 null — 다음 정상 적재 사이클에서 덮인다.
    private CongestionRedisDto toRedisDto(Congestion entity) {
        List<CongestionRedisDto.ForecastDto> forecasts;
        if (entity.getForecast() == null || entity.getForecast().isBlank()) {
            forecasts = Collections.emptyList();
        } else {
            try {
                forecasts = objectMapper.readValue(
                        entity.getForecast(),
                        new TypeReference<List<CongestionRedisDto.ForecastDto>>() {}
                );
            } catch (JsonProcessingException e) {
                log.warn("[CongestionService] 백필 forecast 파싱 실패 - areaCode={}", entity.getAreaCode());
                forecasts = Collections.emptyList();
            }
        }
        return new CongestionRedisDto(
                null,
                entity.getAreaCode(),
                entity.getCongestionLevel().getDescription(),
                entity.getCongestionMessage(),
                entity.getMinPeopleCount(),
                entity.getMaxPeopleCount(),
                entity.getPopulationTime() != null
                        ? entity.getPopulationTime().format(POPULATION_TIME_FORMATTER)
                        : null,
                forecasts
        );
    }

    private CongestionResponse toResponse(CongestionRedisDto dto) {
        List<CongestionResponse.ForecastResponse> forecasts = dto.forecasts() == null
                ? Collections.emptyList()
                : dto.forecasts().stream()
                        .map(f -> new CongestionResponse.ForecastResponse(
                                f.forecastTime(),
                                f.congestionLevel(),
                                f.minPeopleCount(),
                                f.maxPeopleCount()
                        ))
                        .toList();
        return new CongestionResponse(
                dto.areaCode(),
                dto.congestionLevel(),
                dto.congestionMessage(),
                dto.minPeopleCount(),
                dto.maxPeopleCount(),
                dto.populationTime(),
                forecasts
        );
    }

    private CongestionResponse toResponse(Congestion entity) {
        List<CongestionResponse.ForecastResponse> forecasts;
        if (entity.getForecast() == null || entity.getForecast().isBlank()) {
            forecasts = Collections.emptyList();
        } else {
            try {
                forecasts = objectMapper.readValue(
                        entity.getForecast(),
                        new TypeReference<List<CongestionResponse.ForecastResponse>>() {}
                );
            } catch (JsonProcessingException e) {
                log.warn("[CongestionService] forecast JSON 파싱 실패 - areaCode={}", entity.getAreaCode(), e);
                forecasts = Collections.emptyList();
            }
        }
        return new CongestionResponse(
                entity.getAreaCode(),
                entity.getCongestionLevel().getDescription(),
                entity.getCongestionMessage(),
                entity.getMinPeopleCount(),
                entity.getMaxPeopleCount(),
                entity.getPopulationTime() != null
                        ? entity.getPopulationTime().format(POPULATION_TIME_FORMATTER)
                        : null,
                forecasts
        );
    }

    private LocalDateTime parsePopulationTime(String time) {
        if (time == null || time.isBlank()) return null;
        try {
            return LocalDateTime.parse(time, POPULATION_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("[CongestionService] populationTime 파싱 실패 - value={}", time);
            return null;
        }
    }
}
