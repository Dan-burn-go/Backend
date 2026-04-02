package com.danburn.congestion.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.dto.response.CongestionResponse;
import com.danburn.congestion.service.CongestionService;
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

@WebMvcTest(controllers = CongestionController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
class CongestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CongestionService congestionService;

    private CongestionResponse createResponse(String areaCode, String level) {
        return new CongestionResponse(
                areaCode, level, "테스트 메시지",
                10000, 12000, "2026-04-01 14:00",
                List.of(new CongestionResponse.ForecastResponse(
                        "2026-04-01 15:00", "보통", 8000, 10000
                ))
        );
    }

    @Test
    @DisplayName("GET /api/congestion → 전체 조회 성공")
    void findAll_success() throws Exception {
        given(congestionService.findAll()).willReturn(List.of(
                createResponse("POI001", "붐빔"),
                createResponse("POI002", "여유")
        ));

        mockMvc.perform(get("/api/congestion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].areaCode").value("POI001"));
    }

    @Test
    @DisplayName("GET /api/congestion → 데이터 없음 시 빈 배열")
    void findAll_empty() throws Exception {
        given(congestionService.findAll()).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/congestion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/congestion/{areaCode} → 단건 조회 성공")
    void findByAreaCode_success() throws Exception {
        given(congestionService.findByAreaCode("POI001"))
                .willReturn(createResponse("POI001", "붐빔"));

        mockMvc.perform(get("/api/congestion/POI001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.areaCode").value("POI001"))
                .andExpect(jsonPath("$.data.congestionLevel").value("붐빔"))
                .andExpect(jsonPath("$.data.forecasts").isArray())
                .andExpect(jsonPath("$.data.forecasts.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/congestion/{areaCode} → 404 에러 응답")
    void findByAreaCode_notFound() throws Exception {
        given(congestionService.findByAreaCode("POI999"))
                .willThrow(new GlobalException(404, "해당 장소의 혼잡도 데이터가 없습니다. areaCode: POI999"));

        mockMvc.perform(get("/api/congestion/POI999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("해당 장소의 혼잡도 데이터가 없습니다. areaCode: POI999"));
    }
}
