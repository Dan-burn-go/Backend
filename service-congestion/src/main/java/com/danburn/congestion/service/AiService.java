package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.AiReport;
import com.danburn.congestion.dto.response.AireportApiResponse;
import com.danburn.congestion.repository.AiReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiReportRepository aiReportRepository;

    public AireportApiResponse getLatestAiReport(String areaCode) {
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