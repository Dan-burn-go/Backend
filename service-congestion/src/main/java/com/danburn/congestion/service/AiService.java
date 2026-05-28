package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.AiReport;
import com.danburn.congestion.dto.response.AireportApiResponse;
import com.danburn.congestion.event.AiReportEvent;
import com.danburn.congestion.repository.AiReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    // Redis(4h) 미스 시 DB 폴백은 최근 이 기간 이내 리포트만 — 오래된 리포트 stale 노출 방지.
    private static final Duration DB_FALLBACK_TTL = Duration.ofHours(3);

    private final AiReportRepository aiReportRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AireportApiResponse getLatestAiReport(String areaCode) {
        // 1. Redis에서 조회
        String json = stringRedisTemplate.opsForValue().get("ai-report:" + areaCode);
        if (json != null) {
            try {
                AiReportEvent event = objectMapper.readValue(json, AiReportEvent.class);
                return new AireportApiResponse(
                    event.areaCode(),
                    event.areaName(),
                    event.analysisMessage(),
                    event.populationTime()
                );
            } catch (JsonProcessingException e) {
                log.error("[AiService] Redis 조회 실패 - areaCode={}", areaCode);
            }
        }

        // 2. DB에서 조회 — 최근 DB_FALLBACK_TTL 이내 리포트만 (오래된 stale 리포트 제외)
        Instant cutoff = Instant.now().minus(DB_FALLBACK_TTL);
        AiReport aiReport = aiReportRepository
            .findTopByAreaCodeAndCreatedAtAfterOrderByCreatedAtDesc(areaCode, cutoff)
            .orElseThrow(() -> new GlobalException(404, "AI 리포트 데이터가 없습니다. areaCode: " + areaCode));
        return new AireportApiResponse(
            aiReport.getAreaCode(),
            aiReport.getAreaName(),
            aiReport.getAnalysisMessage(),
            aiReport.getPopulationTime()
        );
    }
}