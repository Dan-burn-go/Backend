package com.danburn.map.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.map.dto.response.NearbyPlaceResponse;
import com.danburn.map.service.NearbyPlaceService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NearbyPlaceController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
class NearbyPlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NearbyPlaceService nearbyPlaceService;

    private NearbyPlaceResponse createResponse(String placeName) {
        return new NearbyPlaceResponse(placeName, "음식점 > 한식", "서울 마포구", "서울 마포구 어딘가", "https://place.map.kakao.com/123", 126.9769, 37.5759);
    }

    @Test
    @DisplayName("GET /api/map/nearby-places → 정상 조회 성공")
    void getNearbyPlaces_success() throws Exception {
        given(nearbyPlaceService.getNearbyPlaces(anyString(), anyString()))
                .willReturn(List.of(createResponse("맛집"), createResponse("카페")));

        mockMvc.perform(get("/api/map/nearby-places")
                        .param("areaCode", "POI009")
                        .param("categoryCode", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].placeName").value("맛집"));
    }

    @Test
    @DisplayName("GET /api/map/nearby-places → 결과 없을 때 빈 배열 반환")
    void getNearbyPlaces_empty() throws Exception {
        given(nearbyPlaceService.getNearbyPlaces(anyString(), anyString()))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/map/nearby-places")
                        .param("areaCode", "POI009")
                        .param("categoryCode", "FD6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/map/nearby-places → 잘못된 카테고리 코드 400")
    void getNearbyPlaces_invalidCategoryCode() throws Exception {
        mockMvc.perform(get("/api/map/nearby-places")
                        .param("areaCode", "POI009")
                        .param("categoryCode", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/map/nearby-places → 존재하지 않는 areaCode 404")
    void getNearbyPlaces_invalidAreaCode_notFound() throws Exception {
        willThrow(new GlobalException(404, "존재하지 않는 지역 코드입니다: UNKNOWN"))
                .given(nearbyPlaceService).getNearbyPlaces("UNKNOWN", "ALL");

        mockMvc.perform(get("/api/map/nearby-places")
                        .param("areaCode", "UNKNOWN")
                        .param("categoryCode", "ALL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/map/nearby-places → categoryCode 기본값 ALL로 조회")
    void getNearbyPlaces_defaultCategoryCode() throws Exception {
        given(nearbyPlaceService.getNearbyPlaces(anyString(), anyString()))
                .willReturn(List.of(createResponse("맛집")));

        mockMvc.perform(get("/api/map/nearby-places")
                        .param("areaCode", "POI009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}
