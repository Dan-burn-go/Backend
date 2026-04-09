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

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

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

        // 2. DB에서 조회
        AiReport aiReport = aiReportRepository.findTopByAreaCodeOrderByCreatedAtDesc(areaCode)
            .orElseThrow(() -> new GlobalException(404, "AI 리포트 데이터가 없습니다. areaCode: " + areaCode));
        return new AireportApiResponse(
            aiReport.getAreaCode(),
            aiReport.getAreaName(),
            aiReport.getAnalysisMessage(),
            aiReport.getPopulationTime()
        );
    }
}