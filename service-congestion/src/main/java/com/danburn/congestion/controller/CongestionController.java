package com.danburn.congestion.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.congestion.dto.response.CongestionResponse;
import com.danburn.congestion.service.CongestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "혼잡도", description = "실시간 혼잡도 조회 API")
@RestController
@RequestMapping("/api/congestion")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionService congestionService;

    @Operation(summary = "전체 혼잡도 조회", description = "122개 서울 주요 장소의 실시간 혼잡도를 조회합니다.")
    @GetMapping
    public ApiResponse<List<CongestionResponse>> findAll() {
        return ApiResponse.ok(congestionService.findAll());
    }

    @Operation(summary = "장소별 혼잡도 조회", description = "areaCode로 특정 장소의 실시간 혼잡도를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "해당 장소의 혼잡도 데이터 없음")
    })
    @GetMapping("/{areaCode}")
    public ApiResponse<CongestionResponse> findByAreaCode(
            @Parameter(description = "장소 코드 (예: POI001)", example = "POI001")
            @PathVariable String areaCode) {
        return ApiResponse.ok(congestionService.findByAreaCode(areaCode));
    }
}
