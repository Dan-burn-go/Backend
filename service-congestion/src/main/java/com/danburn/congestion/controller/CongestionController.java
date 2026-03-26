package com.danburn.congestion.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.congestion.dto.response.CongestionResponse;
import com.danburn.congestion.service.CongestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/congestion")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionService congestionService;

    @GetMapping
    public ApiResponse<List<CongestionResponse>> findAll() {
        return ApiResponse.ok(congestionService.findAll());
    }

    @GetMapping("/{locationId}")
    public ApiResponse<CongestionResponse> findByLocationId(
            @PathVariable Long locationId) {
        return ApiResponse.ok(congestionService.findByLocationId(locationId));
    }
}
