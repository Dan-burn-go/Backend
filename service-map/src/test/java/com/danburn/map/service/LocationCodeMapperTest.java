package com.danburn.map.service;

import com.danburn.map.domain.Location;
import com.danburn.map.repository.LocationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LocationCodeMapperTest {

    @Mock
    private LocationJpaRepository locationJpaRepository;

    @InjectMocks
    private LocationCodeMapper locationCodeMapper;

    private Location buildLocation(Long id, String areaCode, Double lat, Double lon) {
        Location loc = Location.builder()
                .apiAreaCode(areaCode)
                .locationName("테스트 장소")
                .latitude(lat)
                .longitude(lon)
                .category("관광특구")
                .build();
        try {
            Field field = Location.class.getDeclaredField("locationId");
            field.setAccessible(true);
            field.set(loc, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return loc;
    }

    @BeforeEach
    void init() {
        given(locationJpaRepository.findAll()).willReturn(List.of(
                buildLocation(1L, "POI001", 37.5665, 126.9780),
                buildLocation(2L, "POI002", null, null)
        ));
        locationCodeMapper.init();
    }

    @Nested
    @DisplayName("getLocationIdByAreaCode")
    class GetLocationIdByAreaCode {

        @Test
        @DisplayName("존재하는 areaCode - locationId 반환")
        void existingAreaCode_returnsId() {
            Optional<Long> result = locationCodeMapper.getLocationIdByAreaCode("POI001");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 areaCode - Optional.empty 반환")
        void unknownAreaCode_returnsEmpty() {
            Optional<Long> result = locationCodeMapper.getLocationIdByAreaCode("UNKNOWN");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCoordinateByAreaCode")
    class GetCoordinateByAreaCode {

        @Test
        @DisplayName("좌표가 있는 areaCode - Coordinate 반환")
        void existingAreaCodeWithCoordinate_returnsCoordinate() {
            Optional<LocationCodeMapper.Coordinate> result = locationCodeMapper.getCoordinateByAreaCode("POI001");
            assertThat(result).isPresent();
            assertThat(result.get().latitude()).isEqualTo(37.5665);
            assertThat(result.get().longitude()).isEqualTo(126.9780);
        }

        @Test
        @DisplayName("좌표가 null인 areaCode - Optional.empty 반환")
        void areaCodeWithNullCoordinate_returnsEmpty() {
            Optional<LocationCodeMapper.Coordinate> result = locationCodeMapper.getCoordinateByAreaCode("POI002");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 areaCode - Optional.empty 반환")
        void unknownAreaCode_returnsEmpty() {
            Optional<LocationCodeMapper.Coordinate> result = locationCodeMapper.getCoordinateByAreaCode("NOTEXIST");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("init - @PostConstruct 캐시 초기화")
    class Init {

        @Test
        @DisplayName("init 호출 시 findAll 1회 실행")
        void init_callsFindAllOnce() {
            // init() was already called in @BeforeEach; call once more to verify
            locationCodeMapper.init();
            then(locationJpaRepository).should(org.mockito.Mockito.times(2)).findAll();
        }

        @Test
        @DisplayName("빈 목록 - 맵이 비어있어 어떤 areaCode도 미반환")
        void emptyLocations_allLookupsReturnEmpty() {
            given(locationJpaRepository.findAll()).willReturn(List.of());
            locationCodeMapper.init();

            assertThat(locationCodeMapper.getLocationIdByAreaCode("POI001")).isEmpty();
            assertThat(locationCodeMapper.getCoordinateByAreaCode("POI001")).isEmpty();
        }
    }
}
