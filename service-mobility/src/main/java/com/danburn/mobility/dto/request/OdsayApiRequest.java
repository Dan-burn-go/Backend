package com.danburn.mobility.dto.request;

public record OdsayApiRequest(
        double originLng,
        double originLat,
        double destLng,
        double destLat
) {}
