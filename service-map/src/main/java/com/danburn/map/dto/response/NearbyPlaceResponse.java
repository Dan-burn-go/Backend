package com.danburn.map.dto.response;

public record NearbyPlaceResponse(
        String placeName,
        String categoryName,
        String addressName,
        String roadAddressName,
        String placeUrl,
        double longitude,
        double latitude
) {
}
