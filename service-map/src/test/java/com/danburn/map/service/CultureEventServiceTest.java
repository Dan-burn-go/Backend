package com.danburn.map.service;

import com.danburn.map.domain.Event;
import com.danburn.map.dto.response.CultureEventResponse;
import com.danburn.map.repository.EventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CultureEventServiceTest {

    @Mock
    private EventJpaRepository eventJpaRepository;

    @InjectMocks
    private CultureEventService cultureEventService;

    private static final double LAT = 37.5759;
    private static final double LON = 126.9769;
    private static final double RADIUS = 1000.0;

    private Event buildEvent(String title, String place) {
        return Event.builder()
                .eventTitle(title)
                .place(place)
                .codename("전시/미술")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .useFee("무료")
                .orgLink("https://link")
                .mainImg("https://img")
                .latitude(LAT)
                .longitude(LON)
                .build();
    }

    @Nested
    @DisplayName("getCultureEvents")
    class GetCultureEvents {

        @Test
        @DisplayName("반경 내 행사 존재 - 행사 목록 반환")
        void eventsWithinRadius_returnsList() {
            given(eventJpaRepository.findEventsWithinRadius(LAT, LON, RADIUS))
                    .willReturn(List.of(buildEvent("서울 전시회", "광화문"), buildEvent("마포 축제", "홍대")));

            List<CultureEventResponse> result = cultureEventService.getCultureEvents(LAT, LON);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).title()).isEqualTo("서울 전시회");
            assertThat(result.get(1).title()).isEqualTo("마포 축제");
        }

        @Test
        @DisplayName("반경 내 행사 없음 - 빈 리스트 반환")
        void noEventsWithinRadius_returnsEmptyList() {
            given(eventJpaRepository.findEventsWithinRadius(LAT, LON, RADIUS))
                    .willReturn(List.of());

            List<CultureEventResponse> result = cultureEventService.getCultureEvents(LAT, LON);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("반환된 Event → CultureEventResponse 필드 매핑 검증")
        void eventToResponse_fieldMapping() {
            Event event = buildEvent("테스트 행사", "테스트 장소");
            given(eventJpaRepository.findEventsWithinRadius(LAT, LON, RADIUS))
                    .willReturn(List.of(event));

            List<CultureEventResponse> result = cultureEventService.getCultureEvents(LAT, LON);

            CultureEventResponse r = result.get(0);
            assertThat(r.title()).isEqualTo("테스트 행사");
            assertThat(r.place()).isEqualTo("테스트 장소");
            assertThat(r.codename()).isEqualTo("전시/미술");
            assertThat(r.startDate()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(r.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
            assertThat(r.useFee()).isEqualTo("무료");
            assertThat(r.latitude()).isEqualTo(LAT);
            assertThat(r.longitude()).isEqualTo(LON);
        }

        @Test
        @DisplayName("정확히 1000m 반경으로 findEventsWithinRadius 호출")
        void callsRepositoryWithCorrectRadius() {
            given(eventJpaRepository.findEventsWithinRadius(LAT, LON, RADIUS))
                    .willReturn(List.of());

            cultureEventService.getCultureEvents(LAT, LON);

            then(eventJpaRepository).should().findEventsWithinRadius(LAT, LON, RADIUS);
        }
    }
}
