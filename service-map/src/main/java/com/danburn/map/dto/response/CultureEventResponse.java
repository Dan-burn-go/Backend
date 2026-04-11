package com.danburn.map.dto.response;

import java.time.LocalDate;

public record CultureEventResponse(
    String title,
    String place,
    String codename,
    LocalDate startDate,
    LocalDate endDate,
    String useFee,
    String orgLink,
    String mainImg,
    Double latitude,
    Double longitude
) {}
