package com.danburn.congestion.service;

import com.danburn.congestion.domain.Congestion;
import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CongestionStateTrackerTest {

    @Mock
    private CongestionRedisRepository congestionRedisRepository;

    @Mock
    private CongestionJpaRepository congestionJpaRepository;

    @InjectMocks
    private CongestionStateTracker tracker;

    private CongestionRedisDto dto(String areaCode, String level) {
        return new CongestionRedisDto(
                "테스트", areaCode, level, "메시지",
                10000, 12000, "2026-04-01 14:00", List.of()
        );
    }

    private Congestion entity(String areaCode, CongestionLevel level) {
        return Congestion.builder()
                .areaCode(areaCode)
                .congestionLevel(level)
                .congestionMessage("메시지")
                .minPeopleCount(10000)
                .maxPeopleCount(12000)
                .forecast("[]")
                .build();
    }

    @Nested
    @DisplayName("filterAreaCodesForAnalysis")
    class FilterAreaCodesForAnalysis {

        @Test
        @DisplayName("BUSY 지역 없으면 빈 목록")
        void noBusy() {
            List<String> result = tracker.filterAreaCodesForAnalysis(List.of(dto("POI001", "보통")));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("이전 상태 없음(최초 감지) → BUSY 지역 포함")
        void firstDetection() {
            given(congestionRedisRepository.findAllByAreaCodes(List.of("POI001")))
                    .willReturn(Arrays.asList((CongestionRedisDto) null));
            given(congestionJpaRepository.findLatestByAreaCodes(List.of("POI001")))
                    .willReturn(List.of());

            List<String> result = tracker.filterAreaCodesForAnalysis(List.of(dto("POI001", "붐빔")));

            assertThat(result).containsExactly("POI001");
        }

        @Test
        @DisplayName("이전 non-BUSY → 상승 엣지 감지")
        void risingEdge() {
            given(congestionRedisRepository.findAllByAreaCodes(List.of("POI001")))
                    .willReturn(List.of(dto("POI001", "보통")));

            List<String> result = tracker.filterAreaCodesForAnalysis(List.of(dto("POI001", "붐빔")));

            assertThat(result).containsExactly("POI001");
        }

        @Test
        @DisplayName("이전 BUSY → 상승 엣지 없음")
        void alreadyBusy() {
            given(congestionRedisRepository.findAllByAreaCodes(List.of("POI001")))
                    .willReturn(List.of(dto("POI001", "붐빔")));

            List<String> result = tracker.filterAreaCodesForAnalysis(List.of(dto("POI001", "붐빔")));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Redis 미스 → DB 폴백으로 이전 상태 조회")
        void redisMissFallbackToDb() {
            given(congestionRedisRepository.findAllByAreaCodes(List.of("POI001")))
                    .willReturn(Arrays.asList((CongestionRedisDto) null));
            given(congestionJpaRepository.findLatestByAreaCodes(List.of("POI001")))
                    .willReturn(List.of(entity("POI001", CongestionLevel.BUSY)));

            List<String> result = tracker.filterAreaCodesForAnalysis(List.of(dto("POI001", "붐빔")));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("filterAreaCodesForRelaxedNotification")
    class FilterAreaCodesForRelaxedNotification {

        @Test
        @DisplayName("non-BUSY 지역 없으면 빈 목록")
        void allBusy() {
            List<String> result = tracker.filterAreaCodesForRelaxedNotification(List.of(dto("POI001", "붐빔")));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("이전 BUSY → 하강 엣지 감지")
        void fallingEdge() {
            given(congestionRedisRepository.findAllByAreaCodes(List.of("POI001")))
                    .willReturn(List.of(dto("POI001", "붐빔")));

            List<String> result = tracker.filterAreaCodesForRelaxedNotification(List.of(dto("POI001", "보통")));

            assertThat(result).containsExactly("POI001");
        }

        @Test
        @DisplayName("이전 non-BUSY → 하강 엣지 없음")
        void alreadyNotBusy() {
            given(congestionRedisRepository.findAllByAreaCodes(List.of("POI001")))
                    .willReturn(List.of(dto("POI001", "보통")));

            List<String> result = tracker.filterAreaCodesForRelaxedNotification(List.of(dto("POI001", "여유")));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("congestionLevel null → 해당 dto 건너뜀")
        void nullLevel() {
            CongestionRedisDto nullLevelDto = new CongestionRedisDto(
                    "명동", "POI001", null, "메시지", 10000, 12000, "2026-04-01 14:00", List.of()
            );

            List<String> result = tracker.filterAreaCodesForRelaxedNotification(List.of(nullLevelDto));

            assertThat(result).isEmpty();
        }
    }
}
