package com.danburn.map.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.map.dto.response.AlternativeLocationResponse;
import com.danburn.map.service.AlternativeLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "대체지역", description = "대체지역 추천 API")
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class AlternativeLocationController {

    private final AlternativeLocationService alternativeLocationService;

    @Operation(summary = "대체지역 추천 조회", description = "혼잡 지역으로부터 거리와 분위기, 카테고리 기반으로 대체지역 3곳을 조회합니다.")
    @GetMapping("/alternative-location")
    public ApiResponse<List<AlternativeLocationResponse>> getAlternativeLocation(
            @Parameter(description = "장소 코드 (예: POI009)", example = "POI009")
            @RequestParam String areaCode) {

        return ApiResponse.ok(alternativeLocationService.getAlternativeLocations(areaCode));
    }
}
