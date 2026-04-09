package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionRankingResponse;
import com.danburn.congestion.dto.response.CongestionTrendResponse;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CongestionAnalysisServiceTest {

    @Mock
    private CongestionJpaRepository congestionJpaRepository;

    @Mock
    private CongestionRedisRepository congestionRedisRepository;

    @InjectMocks
    private CongestionAnalysisService congestionAnalysisService;

    private CongestionRedisDto createRedisDto(String areaCode, String level,
                                               Integer minPeople, Integer maxPeople) {
        return new CongestionRedisDto(
                "테스트장소", areaCode, level, "테스트 메시지",
                minPeople, maxPeople, "2026-04-01 14:00",
                Collections.emptyList()
        );
    }

    @Nested
    @DisplayName("getHourlyTrend")
    class GetHourlyTrend {

        @Test
        @DisplayName("시간별 추이 데이터 조회 성공")
        void success() {
            Object[] row1 = new Object[]{9, "BUSY", 10000.0, 12000.0, 5L};
            Object[] row2 = new Object[]{14, "RELAXED", 3000.0, 5000.0, 3L};

            given(congestionJpaRepository.findHourlyTrend(eq("POI001"), any(LocalDateTime.class)))
                    .willReturn(List.of(row1, row2));

            CongestionTrendResponse result = congestionAnalysisService.getHourlyTrend("POI001", 7);

            assertThat(result.areaCode()).isEqualTo("POI001");
            assertThat(result.areaName()).isEqualTo("강남 MICE 관광특구");
            assertThat(result.data()).hasSize(2);
            assertThat(result.data().get(0).key()).isEqualTo(9);
            assertThat(result.data().get(0).label()).isEqualTo("9시");
            assertThat(result.data().get(0).congestionLevel()).isEqualTo("붐빔");
            assertThat(result.data().get(1).key()).isEqualTo(14);
            assertThat(result.data().get(1).label()).isEqualTo("14시");
            assertThat(result.data().get(1).congestionLevel()).isEqualTo("여유");
        }

        @Test
        @DisplayName("같은 시간대에 여러 레벨 → 가장 빈번한 레벨 선택")
        void multipleLevelsSameHour() {
            Object[] row1 = new Object[]{10, "BUSY", 10000.0, 12000.0, 10L};
            Object[] row2 = new Object[]{10, "NORMAL", 5000.0, 7000.0, 3L};

            given(congestionJpaRepository.findHourlyTrend(eq("POI001"), any(LocalDateTime.class)))
                    .willReturn(List.of(row1, row2));

            CongestionTrendResponse result = congestionAnalysisService.getHourlyTrend("POI001", 7);

            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).congestionLevel()).isEqualTo("붐빔");
            assertThat(result.data().get(0).dataCount()).isEqualTo(13);
        }

        @Test
        @DisplayName("데이터 없는 경우 빈 리스트 반환")
        void emptyData() {
            given(congestionJpaRepository.findHourlyTrend(eq("POI001"), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            CongestionTrendResponse result = congestionAnalysisService.getHourlyTrend("POI001", 7);

            assertThat(result.data()).isEmpty();
        }

        @Test
        @DisplayName("유효하지 않은 areaCode → GlobalException(400)")
        void invalidAreaCode() {
            assertThatThrownBy(() -> congestionAnalysisService.getHourlyTrend("INVALID", 7))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getStatus()).isEqualTo(400));
        }
    }

    @Nested
    @DisplayName("getDailyTrend")
    class GetDailyTrend {

        @Test
        @DisplayName("요일별 추이 데이터 조회 성공")
        void success() {
            Object[] row1 = new Object[]{2, "NORMAL", 5000.0, 7000.0, 10L};
            Object[] row2 = new Object[]{7, "BUSY", 15000.0, 18000.0, 8L};

            given(congestionJpaRepository.findDailyTrend(eq("POI001"), any(LocalDateTime.class)))
                    .willReturn(List.of(row1, row2));

            CongestionTrendResponse result = congestionAnalysisService.getDailyTrend("POI001", 7);

            assertThat(result.areaCode()).isEqualTo("POI001");
            assertThat(result.data()).hasSize(2);
            assertThat(result.data().get(0).key()).isEqualTo(2);
            assertThat(result.data().get(0).label()).isEqualTo("월요일");
            assertThat(result.data().get(0).congestionLevel()).isEqualTo("보통");
            assertThat(result.data().get(1).key()).isEqualTo(7);
            assertThat(result.data().get(1).label()).isEqualTo("토요일");
            assertThat(result.data().get(1).congestionLevel()).isEqualTo("붐빔");
        }

        @Test
        @DisplayName("유효하지 않은 areaCode → GlobalException(400)")
        void invalidAreaCode() {
            assertThatThrownBy(() -> congestionAnalysisService.getDailyTrend("INVALID", 7))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getStatus()).isEqualTo(400));
        }
    }

    @Nested
    @DisplayName("getBusiestRanking")
    class GetBusiestRanking {

        @Test
        @DisplayName("혼잡 순 정렬 → BUSY가 1위")
        void success() {
            given(congestionRedisRepository.findAll()).willReturn(List.of(
                    createRedisDto("POI001", "여유", 1000, 2000),
                    createRedisDto("POI002", "붐빔", 20000, 25000),
                    createRedisDto("POI003", "보통", 5000, 7000)
            ));

            CongestionRankingResponse result = congestionAnalysisService.getBusiestRanking(10);

            assertThat(result.type()).isEqualTo("BUSIEST");
            assertThat(result.totalCount()).isEqualTo(3);
            assertThat(result.rankings().get(0).areaCode()).isEqualTo("POI002");
            assertThat(result.rankings().get(0).rank()).isEqualTo(1);
            assertThat(result.rankings().get(0).congestionLevel()).isEqualTo("붐빔");
        }

        @Test
        @DisplayName("limit 적용 → 상위 N개만 반환")
        void withLimit() {
            given(congestionRedisRepository.findAll()).willReturn(List.of(
                    createRedisDto("POI001", "여유", 1000, 2000),
                    createRedisDto("POI002", "붐빔", 20000, 25000),
                    createRedisDto("POI003", "보통", 5000, 7000)
            ));

            CongestionRankingResponse result = congestionAnalysisService.getBusiestRanking(2);

            assertThat(result.totalCount()).isEqualTo(2);
            assertThat(result.rankings()).hasSize(2);
        }

        @Test
        @DisplayName("Redis 비어있음 → 빈 랭킹 반환")
        void emptyRedis() {
            given(congestionRedisRepository.findAll()).willReturn(Collections.emptyList());

            CongestionRankingResponse result = congestionAnalysisService.getBusiestRanking(10);

            assertThat(result.totalCount()).isEqualTo(0);
            assertThat(result.rankings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRelaxedRanking")
    class GetRelaxedRanking {

        @Test
        @DisplayName("한적 순 정렬 → RELAXED가 1위")
        void success() {
            given(congestionRedisRepository.findAll()).willReturn(List.of(
                    createRedisDto("POI001", "여유", 1000, 2000),
                    createRedisDto("POI002", "붐빔", 20000, 25000),
                    createRedisDto("POI003", "보통", 5000, 7000)
            ));

            CongestionRankingResponse result = congestionAnalysisService.getRelaxedRanking(10);

            assertThat(result.type()).isEqualTo("RELAXED");
            assertThat(result.totalCount()).isEqualTo(3);
            assertThat(result.rankings().get(0).areaCode()).isEqualTo("POI001");
            assertThat(result.rankings().get(0).rank()).isEqualTo(1);
            assertThat(result.rankings().get(0).congestionLevel()).isEqualTo("여유");
        }
    }
}
