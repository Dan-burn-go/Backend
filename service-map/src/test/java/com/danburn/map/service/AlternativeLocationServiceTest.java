package com.danburn.map.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.map.domain.AlternativeLocation;
import com.danburn.map.domain.Location;
import com.danburn.map.dto.response.AlternativeLocationResponse;
import com.danburn.map.infra.CongestionApiClient;
import com.danburn.map.repository.AlternativeLocationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AlternativeLocationServiceTest {

    @Mock
    private LocationCodeMapper locationCodeMapper;

    @Mock
    private AlternativeLocationJpaRepository alternativeLocationJpaRepository;

    @Mock
    private CongestionApiClient congestionApiClient;

    @InjectMocks
    private AlternativeLocationService alternativeLocationService;

    private static final String AREA_CODE = "POI009";
    private static final Long LOCATION_ID = 1L;

    private Location buildLocation(String areaCode, String name, double lat, double lon) {
        return Location.builder()
                .apiAreaCode(areaCode)
                .locationName(name)
                .latitude(lat)
                .longitude(lon)
                .category("관광특구")
                .build();
    }

    private AlternativeLocation buildAlternative(Location main, Location alt, Integer priority) {
        return AlternativeLocation.builder()
                .location(main)
                .alternativeLocation(alt)
                .priority(priority)
                .build();
    }

    @BeforeEach
    void setUp() {
        lenient().when(locationCodeMapper.getLocationIdByAreaCode(AREA_CODE)).thenReturn(Optional.of(LOCATION_ID));
    }

    @Nested
    @DisplayName("getAlternativeLocations - 정상 조회")
    class NormalCases {

        @Test
        @DisplayName("혼잡도 포함 대체지역 목록 반환")
        void returnsListWithCongestionLevel() {
            Location main = buildLocation(AREA_CODE, "광화문", 37.5759, 126.9769);
            Location alt1 = buildLocation("ALT001", "북촌", 37.58, 126.98);
            AlternativeLocation altLoc = buildAlternative(main, alt1, 1);

            given(alternativeLocationJpaRepository.findAlternativeLocationIdOrderByPriority(LOCATION_ID))
                    .willReturn(List.of(altLoc));
            given(congestionApiClient.getCongestionLevel("ALT001"))
                    .willReturn(Mono.just("여유"));

            List<AlternativeLocationResponse> result = alternativeLocationService.getAlternativeLocations(AREA_CODE);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).areaCode()).isEqualTo("ALT001");
            assertThat(result.get(0).congestionLevel()).isEqualTo("여유");
            assertThat(result.get(0).priority()).isEqualTo(1);
        }

        @Test
        @DisplayName("혼잡도 조회 실패 시 null로 포함 반환")
        void congestionApiEmpty_returnsNullCongestionLevel() {
            Location main = buildLocation(AREA_CODE, "광화문", 37.5759, 126.9769);
            Location alt1 = buildLocation("ALT001", "북촌", 37.58, 126.98);
            AlternativeLocation altLoc = buildAlternative(main, alt1, 1);

            given(alternativeLocationJpaRepository.findAlternativeLocationIdOrderByPriority(LOCATION_ID))
                    .willReturn(List.of(altLoc));
            given(congestionApiClient.getCongestionLevel("ALT001"))
                    .willReturn(Mono.empty());

            List<AlternativeLocationResponse> result = alternativeLocationService.getAlternativeLocations(AREA_CODE);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).congestionLevel()).isNull();
        }

        @Test
        @DisplayName("혼잡도 기준으로 정렬 - 여유 < 보통 < 약간 붐빔 < 붐빔")
        void sortsByCongestionLevelOrder() {
            Location main = buildLocation(AREA_CODE, "광화문", 37.5759, 126.9769);
            Location alt1 = buildLocation("ALT001", "북촌", 37.58, 126.98);
            Location alt2 = buildLocation("ALT002", "인사동", 37.57, 126.97);
            Location alt3 = buildLocation("ALT003", "명동", 37.56, 126.96);

            given(alternativeLocationJpaRepository.findAlternativeLocationIdOrderByPriority(LOCATION_ID))
                    .willReturn(List.of(
                            buildAlternative(main, alt1, 1),
                            buildAlternative(main, alt2, 2),
                            buildAlternative(main, alt3, 3)
                    ));
            given(congestionApiClient.getCongestionLevel("ALT001")).willReturn(Mono.just("붐빔"));
            given(congestionApiClient.getCongestionLevel("ALT002")).willReturn(Mono.just("여유"));
            given(congestionApiClient.getCongestionLevel("ALT003")).willReturn(Mono.just("보통"));

            List<AlternativeLocationResponse> result = alternativeLocationService.getAlternativeLocations(AREA_CODE);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).congestionLevel()).isEqualTo("여유");
            assertThat(result.get(1).congestionLevel()).isEqualTo("보통");
            assertThat(result.get(2).congestionLevel()).isEqualTo("붐빔");
        }

        @Test
        @DisplayName("혼잡도 null인 항목 - 정렬 마지막")
        void nullCongestion_sortedLast() {
            Location main = buildLocation(AREA_CODE, "광화문", 37.5759, 126.9769);
            Location alt1 = buildLocation("ALT001", "북촌", 37.58, 126.98);
            Location alt2 = buildLocation("ALT002", "인사동", 37.57, 126.97);

            given(alternativeLocationJpaRepository.findAlternativeLocationIdOrderByPriority(LOCATION_ID))
                    .willReturn(List.of(
                            buildAlternative(main, alt1, 1),
                            buildAlternative(main, alt2, 2)
                    ));
            given(congestionApiClient.getCongestionLevel("ALT001")).willReturn(Mono.empty());
            given(congestionApiClient.getCongestionLevel("ALT002")).willReturn(Mono.just("여유"));

            List<AlternativeLocationResponse> result = alternativeLocationService.getAlternativeLocations(AREA_CODE);

            assertThat(result.get(0).areaCode()).isEqualTo("ALT002");
            assertThat(result.get(1).areaCode()).isEqualTo("ALT001");
        }

        @Test
        @DisplayName("대체지역 없음 - 빈 리스트 반환")
        void noAlternatives_returnsEmptyList() {
            given(alternativeLocationJpaRepository.findAlternativeLocationIdOrderByPriority(LOCATION_ID))
                    .willReturn(List.of());

            List<AlternativeLocationResponse> result = alternativeLocationService.getAlternativeLocations(AREA_CODE);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAlternativeLocations - 예외")
    class ExceptionCases {

        @Test
        @DisplayName("존재하지 않는 areaCode - GlobalException 404")
        void unknownAreaCode_throwsGlobalException404() {
            given(locationCodeMapper.getLocationIdByAreaCode("INVALID"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> alternativeLocationService.getAlternativeLocations("INVALID"))
                    .isInstanceOf(GlobalException.class)
                    .hasMessageContaining("INVALID");
        }
    }
}
