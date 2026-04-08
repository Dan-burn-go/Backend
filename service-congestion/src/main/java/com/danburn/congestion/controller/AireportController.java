package com.danburn.congestion.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.congestion.dto.response.AireportApiResponse;
import com.danburn.congestion.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 리포트", description = "AI 리포트 조회 API")
@RestController
@RequestMapping("/api/aireport")
@RequiredArgsConstructor
public class AireportController {

    private final AiService aiService;

    @Operation(summary = "AI 리포트 조회", description = "areaCode로 특정 장소의 AI 리포트를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "해당 장소의 AI 리포트 데이터 없음")
    })
    @GetMapping("/{areaCode}")
    public ApiResponse<AireportApiResponse> getLatestAiReport(
            @Parameter(description = "장소 코드 (예: POI001)", example = "POI001")
            @PathVariable String areaCode) {
        return ApiResponse.ok(aiService.getLatestAiReport(areaCode));
    }
}