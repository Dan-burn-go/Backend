package com.danburn.congestion.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.dto.response.AireportApiResponse;
import com.danburn.congestion.service.AiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AireportController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
class AireportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiService aiService;

    @Test
    @DisplayName("GET /api/aireport/{areaCode} → 조회 성공")
    void getLatestAiReport_success() throws Exception {
        AireportApiResponse response = new AireportApiResponse(
                "POI001", "광화문·덕수궁", "주말 나들이 인파로 인한 혼잡", "2026-04-01 14:00"
        );
        given(aiService.getLatestAiReport("POI001")).willReturn(response);

        mockMvc.perform(get("/api/aireport/POI001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.areaCode").value("POI001"))
                .andExpect(jsonPath("$.data.areaName").value("광화문·덕수궁"))
                .andExpect(jsonPath("$.data.analysisMessage").value("주말 나들이 인파로 인한 혼잡"))
                .andExpect(jsonPath("$.data.populationTime").value("2026-04-01 14:00"));
    }

    @Test
    @DisplayName("GET /api/aireport/{areaCode} → 404 에러 응답")
    void getLatestAiReport_notFound() throws Exception {
        given(aiService.getLatestAiReport("POI999"))
                .willThrow(new GlobalException(404, "AI 리포트 데이터가 없습니다. areaCode: POI999"));

        mockMvc.perform(get("/api/aireport/POI999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("AI 리포트 데이터가 없습니다. areaCode: POI999"));
    }
}
