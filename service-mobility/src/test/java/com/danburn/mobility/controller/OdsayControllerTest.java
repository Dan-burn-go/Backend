package com.danburn.mobility.controller;

import com.danburn.common.exception.GlobalException;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import com.danburn.mobility.service.OdsayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OdsayController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
@DisplayName("OdsayController 단위 테스트")
class OdsayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OdsayService odsayService;

    private static final String URL = "/api/mobility/route";

    private TransitRouteResponse emptyResponse() {
        return new TransitRouteResponse(List.of());
    }

    @Nested
    @DisplayName("GET /api/mobility/route")
    class GetRoute {

        @Test
        @DisplayName("유효한 좌표 파라미터로 요청하면 200과 경로 목록을 반환한다")
        void valid_params_returns_200() throws Exception {
            given(odsayService.fetchOdsayRoute(any())).willReturn(emptyResponse());

            mockMvc.perform(get(URL)
                            .param("originLng", "127.1272127")
                            .param("originLat", "37.3213399")
                            .param("destLng", "127.028001")
                            .param("destLat", "37.498086"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.paths").isArray());
        }

        @Test
        @DisplayName("경로가 있을 때 paths 배열 크기가 응답에 반영된다")
        void response_paths_reflected_in_data() throws Exception {
            TransitRouteResponse.Info info =
                    new TransitRouteResponse.Info(30, 1500, 1, 0, "출발역", "도착역");
            TransitRouteResponse.Path path =
                    new TransitRouteResponse.Path(info, List.of());
            given(odsayService.fetchOdsayRoute(any()))
                    .willReturn(new TransitRouteResponse(List.of(path, path)));

            mockMvc.perform(get(URL)
                            .param("originLng", "127.1272127")
                            .param("originLat", "37.3213399")
                            .param("destLng", "127.028001")
                            .param("destLat", "37.498086"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paths.length()").value(2));
        }

        @Test
        @DisplayName("필수 파라미터 originLng 누락 시 400을 반환한다")
        void missing_originLng_returns_400() throws Exception {
            mockMvc.perform(get(URL)
                            .param("originLat", "37.3213399")
                            .param("destLng", "127.028001")
                            .param("destLat", "37.498086"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("필수 파라미터 destLat 누락 시 400을 반환한다")
        void missing_destLat_returns_400() throws Exception {
            mockMvc.perform(get(URL)
                            .param("originLng", "127.1272127")
                            .param("originLat", "37.3213399")
                            .param("destLng", "127.028001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("originLng 범위 초과(-180~180) 시 400을 반환한다")
        void originLng_out_of_range_returns_400() throws Exception {
            mockMvc.perform(get(URL)
                            .param("originLng", "200.0")
                            .param("originLat", "37.3213399")
                            .param("destLng", "127.028001")
                            .param("destLat", "37.498086"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("originLat 범위 초과(-90~90) 시 400을 반환한다")
        void originLat_out_of_range_returns_400() throws Exception {
            mockMvc.perform(get(URL)
                            .param("originLng", "127.1272127")
                            .param("originLat", "100.0")
                            .param("destLng", "127.028001")
                            .param("destLat", "37.498086"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("서비스가 GlobalException(502)을 던지면 해당 상태코드를 반환한다")
        void service_throws_global_exception_returns_mapped_status() throws Exception {
            willThrow(new GlobalException(502, "ODsay API 응답이 올바르지 않습니다."))
                    .given(odsayService).fetchOdsayRoute(any());

            mockMvc.perform(get(URL)
                            .param("originLng", "127.1272127")
                            .param("originLat", "37.3213399")
                            .param("destLng", "127.028001")
                            .param("destLat", "37.498086"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.status").value(502))
                    .andExpect(jsonPath("$.message").value("ODsay API 응답이 올바르지 않습니다."));
        }

        @Test
        @DisplayName("서비스가 예기치 않은 예외를 던지면 500을 반환한다")
        void service_throws_unexpected_exception_returns_500() throws Exception {
            willThrow(new RuntimeException("예상치 못한 오류"))
                    .given(odsayService).fetchOdsayRoute(any());

            mockMvc.perform(get(URL)
                            .param("originLng", "127.1272127")
                            .param("originLat", "37.3213399")
                            .param("destLng", "127.028001")
                            .param("destLat", "37.498086"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500));
        }
    }
}
