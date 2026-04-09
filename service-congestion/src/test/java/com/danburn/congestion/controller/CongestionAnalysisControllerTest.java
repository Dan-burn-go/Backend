package com.danburn.congestion.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.dto.response.CongestionRankingResponse;
import com.danburn.congestion.dto.response.CongestionRankingResponse.RankingEntry;
import com.danburn.congestion.dto.response.CongestionTrendResponse;
import com.danburn.congestion.dto.response.CongestionTrendResponse.TrendData;
import com.danburn.congestion.service.CongestionAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CongestionAnalysisController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
class CongestionAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CongestionAnalysisService congestionAnalysisService;

    @Test
    @DisplayName("GET /api/congestion/analysis/hourly/{areaCode} → 시간별 추이 조회 성공")
    void getHourlyTrend_success() throws Exception {
        CongestionTrendResponse response = new CongestionTrendResponse(
                "POI001", "강남 MICE 관광특구",
                List.of(
                        new TrendData(9, "9시", "붐빔", 10000.0, 12000.0, 5),
                        new TrendData(14, "14시", "여유", 3000.0, 5000.0, 3)
                )
        );
        given(congestionAnalysisService.getHourlyTrend("POI001", 7)).willReturn(response);

        mockMvc.perform(get("/api/congestion/analysis/hourly/POI001").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.areaCode").value("POI001"))
                .andExpect(jsonPath("$.data.areaName").value("강남 MICE 관광특구"))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data.length()").value(2))
                .andExpect(jsonPath("$.data.data[0].key").value(9))
                .andExpect(jsonPath("$.data.data[0].label").value("9시"))
                .andExpect(jsonPath("$.data.data[0].congestionLevel").value("붐빔"));
    }

    @Test
    @DisplayName("GET /api/congestion/analysis/daily/{areaCode} → 요일별 추이 조회 성공")
    void getDailyTrend_success() throws Exception {
        CongestionTrendResponse response = new CongestionTrendResponse(
                "POI001", "강남 MICE 관광특구",
                List.of(
                        new TrendData(2, "월요일", "보통", 5000.0, 7000.0, 10),
                        new TrendData(7, "토요일", "붐빔", 15000.0, 18000.0, 8)
                )
        );
        given(congestionAnalysisService.getDailyTrend("POI001", 7)).willReturn(response);

        mockMvc.perform(get("/api/congestion/analysis/daily/POI001").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.areaCode").value("POI001"))
                .andExpect(jsonPath("$.data.data.length()").value(2))
                .andExpect(jsonPath("$.data.data[0].label").value("월요일"))
                .andExpect(jsonPath("$.data.data[1].label").value("토요일"));
    }

    @Test
    @DisplayName("GET /api/congestion/analysis/ranking/busiest → 혼잡 랭킹 조회 성공")
    void getBusiestRanking_success() throws Exception {
        CongestionRankingResponse response = new CongestionRankingResponse(
                "BUSIEST", 2,
                List.of(
                        new RankingEntry(1, "POI002", "동대문 관광특구", "붐빔", 20000, 25000, "2026-04-01 14:00"),
                        new RankingEntry(2, "POI003", "명동 관광특구", "약간 붐빔", 12000, 15000, "2026-04-01 14:00")
                )
        );
        given(congestionAnalysisService.getBusiestRanking(10)).willReturn(response);

        mockMvc.perform(get("/api/congestion/analysis/ranking/busiest").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("BUSIEST"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.rankings[0].rank").value(1))
                .andExpect(jsonPath("$.data.rankings[0].areaCode").value("POI002"));
    }

    @Test
    @DisplayName("GET /api/congestion/analysis/ranking/relaxed → 한적 랭킹 조회 성공")
    void getRelaxedRanking_success() throws Exception {
        CongestionRankingResponse response = new CongestionRankingResponse(
                "RELAXED", 1,
                List.of(
                        new RankingEntry(1, "POI001", "강남 MICE 관광특구", "여유", 1000, 2000, "2026-04-01 14:00")
                )
        );
        given(congestionAnalysisService.getRelaxedRanking(10)).willReturn(response);

        mockMvc.perform(get("/api/congestion/analysis/ranking/relaxed").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("RELAXED"))
                .andExpect(jsonPath("$.data.rankings[0].congestionLevel").value("여유"));
    }

    @Test
    @DisplayName("유효하지 않은 areaCode → 400 에러")
    void invalidAreaCode_returns400() throws Exception {
        given(congestionAnalysisService.getHourlyTrend("INVALID", 7))
                .willThrow(new GlobalException(400, "유효하지 않은 장소 코드입니다. areaCode: INVALID"));

        mockMvc.perform(get("/api/congestion/analysis/hourly/INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("days 파라미터 미지정 시 기본값 7 적용")
    void defaultDaysParameter() throws Exception {
        CongestionTrendResponse response = new CongestionTrendResponse(
                "POI001", "강남 MICE 관광특구", Collections.emptyList()
        );
        given(congestionAnalysisService.getHourlyTrend("POI001", 7)).willReturn(response);

        mockMvc.perform(get("/api/congestion/analysis/hourly/POI001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.areaCode").value("POI001"));
    }
}
