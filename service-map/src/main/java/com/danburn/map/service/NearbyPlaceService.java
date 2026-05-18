package com.danburn.map.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.map.dto.response.KakaoLocalApiResponse;
import com.danburn.map.dto.response.NearbyPlaceResponse;
import com.danburn.map.infra.KakaoLocalApiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

    private static final List<String> ALL_CATEGORY_CODES = List.of("FD6", "CE7", "AT4", "CT1");
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final TypeReference<List<NearbyPlaceResponse>> NEARBY_PLACE_LIST_TYPE = new TypeReference<>() {};

    private final KakaoLocalApiClient kakaoLocalApiClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final LocationCodeMapper locationCodeMapper;

    public List<NearbyPlaceResponse> getNearbyPlaces(String areaCode, String categoryCode) {
        LocationCodeMapper.Coordinate coordinate = locationCodeMapper.getCoordinateByAreaCode(areaCode)
                .orElseThrow(() -> new GlobalException(404, "존재하지 않는 지역 코드입니다: " + areaCode));

        double longitude = coordinate.longitude();
        double latitude = coordinate.latitude();

        if ("ALL".equals(categoryCode)) {
            return ALL_CATEGORY_CODES.parallelStream()
                    .flatMap(code -> fetchWithCache(areaCode, code, longitude, latitude).stream())
                    .toList();
        }
        return fetchWithCache(areaCode, categoryCode, longitude, latitude);
    }

    private List<NearbyPlaceResponse> fetchWithCache(String areaCode, String categoryCode, double longitude, double latitude) {
        String cacheKey = "map:nearby:" + areaCode + ":" + categoryCode;

        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, NEARBY_PLACE_LIST_TYPE);
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 읽기 실패 - key: {}", cacheKey, e);
        }

        List<NearbyPlaceResponse> result = fetchFromKakao(categoryCode, longitude, latitude);

        if (!result.isEmpty()) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), CACHE_TTL);
            } catch (Exception e) {
                log.warn("Redis 캐시 쓰기 실패 - key: {}", cacheKey, e);
            }
        }

        return result;
    }

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
            log.warn("카카오 API 호출 실패 - categoryCode: {}", categoryCode, e);
            return List.of();
        }
    }
}
