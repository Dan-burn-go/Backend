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

@Slf4j
@Service
@RequiredArgsConstructor
public class CongestionService {

    private static final DateTimeFormatter POPULATION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CongestionRedisRepository congestionRedisRepository;
    private final CongestionJpaRepository congestionJpaRepository;
    private final ObjectMapper objectMapper;

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

        log.warn("Redis 캐시 전체 미스 - DB 폴백 조회");
        return congestionJpaRepository.findLatestPerLocation().stream()
                .map(this::toResponse)
                .toList();
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
