package com.danburn.map.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.map.dto.response.AlternativeLocationResponse;
import com.danburn.map.service.AlternativeLocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AlternativeLocationController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
class AlternativeLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlternativeLocationService alternativeLocationService;

    private AlternativeLocationResponse createResponse(String areaCode, String locationName, String congestionLevel) {
        return new AlternativeLocationResponse(areaCode, locationName, 37.5759, 126.9769, 1, congestionLevel);
    }

    @Test
    @DisplayName("GET /api/map/alternative-location → 정상 조회 성공")
    void getAlternativeLocation_success() throws Exception {
        given(alternativeLocationService.getAlternativeLocations(anyString()))
                .willReturn(List.of(
                        createResponse("ALT001", "북촌", "여유"),
                        createResponse("ALT002", "인사동", "보통")
                ));

        mockMvc.perform(get("/api/map/alternative-location")
                        .param("areaCode", "POI009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].areaCode").value("ALT001"))
                .andExpect(jsonPath("$.data[0].congestionLevel").value("여유"));
    }

    @Test
    @DisplayName("GET /api/map/alternative-location → 결과 없을 때 빈 배열 반환")
    void getAlternativeLocation_empty() throws Exception {
        given(alternativeLocationService.getAlternativeLocations(anyString()))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/map/alternative-location")
                        .param("areaCode", "POI009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/map/alternative-location → 존재하지 않는 areaCode 404")
    void getAlternativeLocation_invalidAreaCode_notFound() throws Exception {
        willThrow(new GlobalException(404, "존재하지 않는 지역 코드입니다: UNKNOWN"))
                .given(alternativeLocationService).getAlternativeLocations("UNKNOWN");

        mockMvc.perform(get("/api/map/alternative-location")
                        .param("areaCode", "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/map/alternative-location → 503 서비스 응답 지연")
    void getAlternativeLocation_serviceUnavailable() throws Exception {
        willThrow(new GlobalException(503, "서비스 응답 지연으로 요청을 처리할 수 없습니다."))
                .given(alternativeLocationService).getAlternativeLocations(anyString());

        mockMvc.perform(get("/api/map/alternative-location")
                        .param("areaCode", "POI009"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("GET /api/map/alternative-location → areaCode 파라미터 누락 시 500 (catch-all handler)")
    void getAlternativeLocation_missingParam_badRequest() throws Exception {
        mockMvc.perform(get("/api/map/alternative-location"))
                .andExpect(status().isInternalServerError());
    }
}
