package com.danburn.map.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.map.dto.response.NearbyPlaceResponse;
import com.danburn.map.service.NearbyPlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "주변 장소", description = "주변 맛집/놀거리 조회 API")
@Validated
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class NearbyPlaceController {

    private final NearbyPlaceService nearbyPlaceService;

    @Operation(summary = "주변 장소 조회", description = "위도/경도 기준 1km 이내 주변 장소를 카테고리별로 조회합니다.")
    @GetMapping("/nearby-places")
    public ApiResponse<List<NearbyPlaceResponse>> getNearbyPlaces(
            @Parameter(description = "카테고리 코드 (ALL/FD6/CE7/AT4/CT1)", example = "ALL")
            @Pattern(regexp = "ALL|FD6|CE7|AT4|CT1", message = "유효하지 않은 카테고리 코드입니다.")
            @RequestParam(defaultValue = "ALL") String categoryCode,
            @Parameter(description = "위도 (33.0 ~ 38.9)", example = "37.5759")
            @RequestParam @DecimalMin("33.0") @DecimalMax("38.9") Double latitude,
            @Parameter(description = "경도 (124.5 ~ 132.0)", example = "126.9769")
            @RequestParam @DecimalMin("124.5") @DecimalMax("132.0") Double longitude) {

        return ApiResponse.ok(nearbyPlaceService.getNearbyPlaces(categoryCode, longitude, latitude));
    }
}
