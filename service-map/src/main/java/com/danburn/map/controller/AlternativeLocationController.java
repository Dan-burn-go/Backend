package com.danburn.map.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.map.dto.response.AlternativeLocationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "대체지역", description = "대체지역 추천 API")
@RestController
@RequestMapping("/api/map")
public class AlternativeLocationController {

    @Operation(summary = "대체지역 추천 조회", description = "혼잡 지역으로부터 거리와 분위기, 카테고리 기반으로 대체지역 3곳을 조회합니다.")
    @GetMapping("/alternative-location")
    public ApiResponse<List<AlternativeLocationResponse>> getAlternativeLocation(
            @Parameter(description = "장소 코드 (예: POI009)", example = "POI009")
            @RequestParam String areaCode) {

        List<AlternativeLocationResponse> mockData = List.of(
            new AlternativeLocationResponse("POI001", "광화문·덕수궁", 37.5759, 126.9769, 1, "여유"),
            new AlternativeLocationResponse("POI002", "인사동·익선동", 37.5742, 126.9855, 2, "보통"),
            new AlternativeLocationResponse("POI003", "북촌한옥마을", 37.5826, 126.9830, 3, "붐빔")
        );

        return ApiResponse.ok(mockData);
    }
}
