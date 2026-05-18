package com.danburn.map.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.map.dto.response.KakaoLocalApiResponse;
import com.danburn.map.dto.response.NearbyPlaceResponse;
import com.danburn.map.infra.KakaoLocalApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class NearbyPlaceServiceTest {

    @Mock
    private KakaoLocalApiClient kakaoLocalApiClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LocationCodeMapper locationCodeMapper;

    @InjectMocks
    private NearbyPlaceService nearbyPlaceService;

    private static final LocationCodeMapper.Coordinate COORDINATE = new LocationCodeMapper.Coordinate(37.5759, 126.9769);

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(locationCodeMapper.getCoordinateByAreaCode("POI009")).thenReturn(Optional.of(COORDINATE));
    }

    private KakaoLocalApiResponse createKakaoResponse(String placeName) {
        return new KakaoLocalApiResponse(
                new KakaoLocalApiResponse.Meta(1, true),
                List.of(new KakaoLocalApiResponse.Document(
                        placeName, "음식점 > 한식", "서울 마포구", "서울 마포구 어딘가",
                        "https://place.map.kakao.com/123", "100", "126.9769", "37.5759"
                ))
        );
    }

    @Nested
    @DisplayName("getNearbyPlaces")
    class GetNearbyPlaces {

        @Test
        @DisplayName("단일 카테고리 - 캐시 미스 시 카카오 API 1회 호출 후 결과 반환")
        void singleCategory_cacheMiss_returnsResult() {
            given(valueOperations.get("map:nearby:POI009:FD6")).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces("FD6", 126.9769, 37.5759))
                    .willReturn(createKakaoResponse("맛집"));

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).placeName()).isEqualTo("맛집");
            then(kakaoLocalApiClient).should().fetchNearbyPlaces("FD6", 126.9769, 37.5759);
        }

        @Test
        @DisplayName("ALL 카테고리 - FD6/CE7/AT4/CT1 4개 카테고리 모두 호출 후 결과 합산")
        void allCategory_callsFourApisAndMergesResults() {
            given(valueOperations.get(anyString())).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces(anyString(), eq(126.9769), eq(37.5759)))
                    .willReturn(createKakaoResponse("장소"));

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "ALL");

            assertThat(result).hasSize(4);
            then(kakaoLocalApiClient).should(times(4)).fetchNearbyPlaces(anyString(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("존재하지 않는 areaCode - GlobalException 404 발생")
        void invalidAreaCode_throwsGlobalException() {
            given(locationCodeMapper.getCoordinateByAreaCode("INVALID")).willReturn(Optional.empty());

            assertThatThrownBy(() -> nearbyPlaceService.getNearbyPlaces("INVALID", "FD6"))
                    .isInstanceOf(GlobalException.class)
                    .hasMessageContaining("INVALID");
        }
    }

    @Nested
    @DisplayName("Redis 캐싱")
    class Caching {

        @Test
        @DisplayName("캐시 히트 - Redis 데이터 반환, 카카오 API 호출 안 함")
        void cacheHit_returnsRedisData_withoutCallingKakao() throws Exception {
            List<NearbyPlaceResponse> cached = List.of(
                    new NearbyPlaceResponse("맛집", "음식점", "서울 마포구", "서울 마포구 어딘가", "https://url", 126.9769, 37.5759)
            );
            String cachedJson = objectMapper.writeValueAsString(cached);
            given(valueOperations.get("map:nearby:POI009:FD6")).willReturn(cachedJson);

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).placeName()).isEqualTo("맛집");
            then(kakaoLocalApiClient).should(never()).fetchNearbyPlaces(any(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("캐시 미스 - 카카오 API 호출 후 Redis에 TTL 1시간으로 저장")
        void cacheMiss_savesToRedisWithTtl() {
            given(valueOperations.get("map:nearby:POI009:FD6")).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces("FD6", 126.9769, 37.5759))
                    .willReturn(createKakaoResponse("맛집"));

            nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            then(valueOperations).should().set(eq("map:nearby:POI009:FD6"), anyString(), eq(Duration.ofHours(1)));
        }

        @Test
        @DisplayName("Redis 읽기 실패 - 카카오 API 폴백 후 결과 정상 반환")
        void redisReadFailure_fallbackToKakao() {
            given(valueOperations.get("map:nearby:POI009:FD6"))
                    .willThrow(new RuntimeException("Redis 연결 실패"));
            given(kakaoLocalApiClient.fetchNearbyPlaces("FD6", 126.9769, 37.5759))
                    .willReturn(createKakaoResponse("맛집"));

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).placeName()).isEqualTo("맛집");
        }

        @Test
        @DisplayName("Redis 쓰기 실패 - 결과 정상 반환")
        void redisWriteFailure_returnsResultNormally() {
            given(valueOperations.get("map:nearby:POI009:FD6")).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces("FD6", 126.9769, 37.5759))
                    .willReturn(createKakaoResponse("맛집"));
            willThrow(new RuntimeException("Redis 쓰기 실패"))
                    .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).placeName()).isEqualTo("맛집");
        }

        @Test
        @DisplayName("카카오 API 빈 배열 반환 - Redis에 저장하지 않음")
        void emptyResult_doesNotCacheToRedis() {
            given(valueOperations.get("map:nearby:POI009:FD6")).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces("FD6", 126.9769, 37.5759))
                    .willReturn(new KakaoLocalApiResponse(
                            new KakaoLocalApiResponse.Meta(0, true), List.of()
                    ));

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            assertThat(result).isEmpty();
            then(valueOperations).should(never()).set(anyString(), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("카카오 API 실패 처리")
    class KakaoApiFailure {

        @Test
        @DisplayName("카카오 API 예외 - 빈 리스트 반환")
        void kakaoApiException_returnsEmptyList() {
            given(valueOperations.get("map:nearby:POI009:FD6")).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces("FD6", 126.9769, 37.5759))
                    .willThrow(new RuntimeException("카카오 API 오류"));

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("카카오 API null 응답 - 빈 리스트 반환")
        void kakaoApiNullResponse_returnsEmptyList() {
            given(valueOperations.get("map:nearby:POI009:FD6")).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces("FD6", 126.9769, 37.5759))
                    .willReturn(null);

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "FD6");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ALL 카테고리 중 일부 카카오 API 실패 - 성공한 카테고리 결과만 반환")
        void allCategory_partialKakaoFailure_returnsPartialResults() {
            given(valueOperations.get(anyString())).willReturn(null);
            given(kakaoLocalApiClient.fetchNearbyPlaces(eq("FD6"), anyDouble(), anyDouble()))
                    .willReturn(createKakaoResponse("맛집"));
            given(kakaoLocalApiClient.fetchNearbyPlaces(eq("CE7"), anyDouble(), anyDouble()))
                    .willThrow(new RuntimeException("카카오 API 오류"));
            given(kakaoLocalApiClient.fetchNearbyPlaces(eq("AT4"), anyDouble(), anyDouble()))
                    .willReturn(createKakaoResponse("관광지"));
            given(kakaoLocalApiClient.fetchNearbyPlaces(eq("CT1"), anyDouble(), anyDouble()))
                    .willReturn(createKakaoResponse("문화시설"));

            List<NearbyPlaceResponse> result = nearbyPlaceService.getNearbyPlaces("POI009", "ALL");

            assertThat(result).hasSize(3);
        }
    }
}
