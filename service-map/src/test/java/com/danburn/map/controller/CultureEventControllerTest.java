package com.danburn.map.controller;

import com.danburn.map.dto.response.CultureEventResponse;
import com.danburn.map.service.CultureEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CultureEventController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
class CultureEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CultureEventService cultureEventService;

    private CultureEventResponse createResponse(String title) {
        return new CultureEventResponse(
                title, "광화문", "전시/미술",
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 12, 31),
                "무료", "https://link", "https://img",
                37.5759, 126.9769
        );
    }

    @Test
    @DisplayName("GET /api/map/culture-events → 정상 조회 성공")
    void getCultureEvents_success() throws Exception {
        given(cultureEventService.getCultureEvents(anyDouble(), anyDouble()))
                .willReturn(List.of(createResponse("서울 전시회"), createResponse("마포 축제")));

        mockMvc.perform(get("/api/map/culture-events")
                        .param("latitude", "37.5759")
                        .param("longitude", "126.9769"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("서울 전시회"));
    }

    @Test
    @DisplayName("GET /api/map/culture-events → 결과 없을 때 빈 배열 반환")
    void getCultureEvents_empty() throws Exception {
        given(cultureEventService.getCultureEvents(anyDouble(), anyDouble()))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/map/culture-events")
                        .param("latitude", "37.5759")
                        .param("longitude", "126.9769"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/map/culture-events → 위도 범위 초과 (38.9 초과) 400")
    void getCultureEvents_latitudeOverMax_badRequest() throws Exception {
        mockMvc.perform(get("/api/map/culture-events")
                        .param("latitude", "39.0")
                        .param("longitude", "126.9769"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/map/culture-events → 위도 범위 미달 (33.0 미만) 400")
    void getCultureEvents_latitudeUnderMin_badRequest() throws Exception {
        mockMvc.perform(get("/api/map/culture-events")
                        .param("latitude", "32.9")
                        .param("longitude", "126.9769"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/map/culture-events → 경도 범위 초과 (132.0 초과) 400")
    void getCultureEvents_longitudeOverMax_badRequest() throws Exception {
        mockMvc.perform(get("/api/map/culture-events")
                        .param("latitude", "37.5759")
                        .param("longitude", "132.1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/map/culture-events → 경도 범위 미달 (124.5 미만) 400")
    void getCultureEvents_longitudeUnderMin_badRequest() throws Exception {
        mockMvc.perform(get("/api/map/culture-events")
                        .param("latitude", "37.5759")
                        .param("longitude", "124.4"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/map/culture-events → latitude 파라미터 누락 시 500 (catch-all handler)")
    void getCultureEvents_missingLatitude_badRequest() throws Exception {
        mockMvc.perform(get("/api/map/culture-events")
                        .param("longitude", "126.9769"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /api/map/culture-events → longitude 파라미터 누락 시 500 (catch-all handler)")
    void getCultureEvents_missingLongitude_badRequest() throws Exception {
        mockMvc.perform(get("/api/map/culture-events")
                        .param("latitude", "37.5759"))
                .andExpect(status().isInternalServerError());
    }
}
