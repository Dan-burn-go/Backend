package com.danburn.map.dto.response;

public record AlternativeLocationResponse(
    String areaCode,
    String locationName,
    Double latitude,
    Double longitude,
    Integer priority,
    String congestionLevel
) {}
