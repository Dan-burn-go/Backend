package com.danburn.map.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.map.dto.response.CultureEventResponse;
import com.danburn.map.service.CultureEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "문화행사", description = "주변 문화행사 조회 API")
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class CultureEventController {

    private final CultureEventService cultureEventService;

    @Operation(summary = "주변 문화행사 조회", description = "위도/경도 기준 1km 이내 문화행사를 조회합니다.")
    @GetMapping("/culture-events")
    public ApiResponse<List<CultureEventResponse>> getCultureEvents(
            @Parameter(description = "위도", example = "37.5759")
            @RequestParam Double latitude,
            @Parameter(description = "경도", example = "126.9769")
            @RequestParam Double longitude) {

        return ApiResponse.ok(cultureEventService.getCultureEvents(latitude, longitude));
    }
}
