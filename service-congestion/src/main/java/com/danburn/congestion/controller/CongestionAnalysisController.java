package com.danburn.congestion.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.congestion.dto.response.CongestionRankingResponse;
import com.danburn.congestion.dto.response.CongestionTrendResponse;
import com.danburn.congestion.service.CongestionAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "혼잡도 분석", description = "혼잡도 통계 추이 및 실시간 랭킹 API")
@RestController
@RequestMapping("/api/congestion/analysis")
@RequiredArgsConstructor
public class CongestionAnalysisController {

    private final CongestionAnalysisService congestionAnalysisService;

    @Operation(summary = "시간별 혼잡도 추이",
            description = "특정 장소의 시간대별(0~23시) 혼잡도 추이를 조회합니다. 그래프 렌더링용 데이터입니다.")
    @GetMapping("/hourly/{areaCode}")
    public ApiResponse<CongestionTrendResponse> getHourlyTrend(
            @Parameter(description = "장소 코드", example = "POI001")
            @PathVariable String areaCode,
            @Parameter(description = "조회 기간 (일 수, 기본 7일)", example = "7")
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(congestionAnalysisService.getHourlyTrend(areaCode, days));
    }

    @Operation(summary = "요일별 혼잡도 추이",
            description = "특정 장소의 요일별 혼잡도 패턴을 조회합니다. 1=일요일 ~ 7=토요일")
    @GetMapping("/daily/{areaCode}")
    public ApiResponse<CongestionTrendResponse> getDailyTrend(
            @Parameter(description = "장소 코드", example = "POI001")
            @PathVariable String areaCode,
            @Parameter(description = "조회 기간 (일 수, 기본 7일)", example = "7")
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(congestionAnalysisService.getDailyTrend(areaCode, days));
    }

    @Operation(summary = "혼잡한 장소 랭킹",
            description = "현재 시점 기준 가장 붐비는 장소 TOP N을 조회합니다.")
    @GetMapping("/ranking/busiest")
    public ApiResponse<CongestionRankingResponse> getBusiestRanking(
            @Parameter(description = "조회 개수 (기본 10)", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(congestionAnalysisService.getBusiestRanking(limit));
    }

    @Operation(summary = "한적한 장소 랭킹",
            description = "현재 시점 기준 가장 한적한 장소 TOP N을 조회합니다.")
    @GetMapping("/ranking/relaxed")
    public ApiResponse<CongestionRankingResponse> getRelaxedRanking(
            @Parameter(description = "조회 개수 (기본 10)", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(congestionAnalysisService.getRelaxedRanking(limit));
    }
}
