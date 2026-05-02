package com.danburn.map.dto.response;

public record CongestionApiResponse(
        int status,
        String message,
        CongestionData data
) {
    public record CongestionData(
            String areaCode,
            String congestionLevel
    ) {}
}
