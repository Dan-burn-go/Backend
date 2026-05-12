package com.danburn.map.service;

import com.danburn.map.dto.response.KakaoLocalApiResponse;
import com.danburn.map.dto.response.NearbyPlaceResponse;
import com.danburn.map.infra.KakaoLocalApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

    private static final List<String> ALL_CATEGORY_CODES = List.of("FD6", "CE7", "AT4", "CT1");

    private final KakaoLocalApiClient kakaoLocalApiClient;

    public List<NearbyPlaceResponse> getNearbyPlaces(String categoryCode, double longitude, double latitude) {
        if ("ALL".equals(categoryCode)) {
            return ALL_CATEGORY_CODES.stream()
                    .flatMap(code -> fetchFromKakao(code, longitude, latitude).stream())
                    .toList();
        }
        return fetchFromKakao(categoryCode, longitude, latitude);
    }

    // TODO: Redis 캐싱 추가 시 이 메서드에 캐시 읽기/쓰기 로직 삽입
    private List<NearbyPlaceResponse> fetchFromKakao(String categoryCode, double longitude, double latitude) {
        try {
            KakaoLocalApiResponse response = kakaoLocalApiClient.fetchNearbyPlaces(categoryCode, longitude, latitude);
            if (response == null || response.documents() == null) {
                return List.of();
            }
            return response.documents().stream()
                    .map(doc -> new NearbyPlaceResponse(
                            doc.placeName(),
                            doc.categoryName(),
                            doc.addressName(),
                            doc.roadAddressName(),
                            doc.placeUrl(),
                            doc.x() != null && !doc.x().isEmpty() ? Double.parseDouble(doc.x()) : 0.0,
                            doc.y() != null && !doc.y().isEmpty() ? Double.parseDouble(doc.y()) : 0.0
                    ))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
