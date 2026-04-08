package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.AiReport;
import com.danburn.congestion.dto.response.AireportApiResponse;
import com.danburn.congestion.repository.AiReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiReportRepository aiReportRepository;

    @InjectMocks
    private AiService aiService;

    private AiReport createAiReport(String areaCode, String areaName) {
        return AiReport.builder()
                .areaCode(areaCode)
                .areaName(areaName)
                .congestionLevel("붐빔")
                .analysisMessage("주말 나들이 인파로 인한 혼잡")
                .populationTime("2026-04-01 14:00")
                .build();
    }

    @Test
    @DisplayName("AI 리포트 조회 성공")
    void getLatestAiReport_success() {
        AiReport report = createAiReport("POI001", "광화문·덕수궁");
        given(aiReportRepository.findTopByAreaCodeOrderByCreatedAtDesc("POI001"))
                .willReturn(Optional.of(report));

        AireportApiResponse result = aiService.getLatestAiReport("POI001");

        assertThat(result.areaCode()).isEqualTo("POI001");
        assertThat(result.areaName()).isEqualTo("광화문·덕수궁");
        assertThat(result.analysisMessage()).isEqualTo("주말 나들이 인파로 인한 혼잡");
        assertThat(result.populationTime()).isEqualTo("2026-04-01 14:00");
    }

    @Test
    @DisplayName("AI 리포트 없음 → GlobalException(404)")
    void getLatestAiReport_notFound() {
        given(aiReportRepository.findTopByAreaCodeOrderByCreatedAtDesc("POI999"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> aiService.getLatestAiReport("POI999"))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getStatus()).isEqualTo(404));
    }
}
