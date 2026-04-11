package com.danburn.map.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.map.dto.response.AlternativeLocationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/map")
public class AlternativeLocationController {

    @GetMapping("/alternative-location")
    public ApiResponse<List<AlternativeLocationResponse>> getAlternativeLocation(
            @RequestParam String areaCode) {

        List<AlternativeLocationResponse> mockData = List.of(
            new AlternativeLocationResponse("POI001", "광화문·덕수궁", 37.5759, 126.9769, 1, "여유"),
            new AlternativeLocationResponse("POI002", "인사동·익선동", 37.5742, 126.9855, 2, "보통"),
            new AlternativeLocationResponse("POI003", "북촌한옥마을", 37.5826, 126.9830, 3, "붐빔")
        );

        return ApiResponse.ok(mockData);
    }
}
