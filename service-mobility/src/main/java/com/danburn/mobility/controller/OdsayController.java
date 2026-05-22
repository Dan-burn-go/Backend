package com.danburn.mobility.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import com.danburn.mobility.service.OdsayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대중교통 경로", description = "대중교통 경로 조회 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mobility")
public class OdsayController {

    private final OdsayService odsayService;

    @Operation(summary = "대중교통 경로 조회", description = "출발지/도착지 좌표 기준 대중교통 경로를 조회합니다.")
    @GetMapping("/route")
    public ApiResponse<TransitRouteResponse> getRoute(
            @Parameter(description = "출발지 경도", example = "127.1272127")
            @DecimalMin("-180.0") @DecimalMax("180.0") @RequestParam double originLng,
            @Parameter(description = "출발지 위도", example = "37.3213399")
            @DecimalMin("-90.0") @DecimalMax("90.0") @RequestParam double originLat,
            @Parameter(description = "도착지 경도", example = "127.02800140627488")
            @DecimalMin("-180.0") @DecimalMax("180.0") @RequestParam double destLng,
            @Parameter(description = "도착지 위도", example = "37.49808633653005")
            @DecimalMin("-90.0") @DecimalMax("90.0") @RequestParam double destLat
    ) {
        return ApiResponse.ok(odsayService.fetchOdsayRoute(new OdsayApiRequest(originLng, originLat, destLng, destLat)));
    }
}
