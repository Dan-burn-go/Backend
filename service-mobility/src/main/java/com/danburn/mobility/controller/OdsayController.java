package com.danburn.mobility.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import com.danburn.mobility.service.OdsayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/mobility")
public class OdsayController {

    private final OdsayService odsayService;

    @GetMapping("/route")
    public ApiResponse<TransitRouteResponse> getRoute(
            @RequestParam Double startLng,
            @RequestParam Double startLat,
            @RequestParam Double endLng,
            @RequestParam Double endLat
    ) {
        OdsayApiRequest odsayApiRequest = new OdsayApiRequest(startLng, startLat, endLng, endLat);
        return ApiResponse.ok(odsayService.fetchOdsayRoute(odsayApiRequest));
    }
}
