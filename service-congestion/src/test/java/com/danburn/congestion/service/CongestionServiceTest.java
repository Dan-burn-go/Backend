package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.Congestion;
import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionResponse;
import com.danburn.congestion.repository.CongestionJpaRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CongestionServiceTest {

    @Mock
    private CongestionRedisRepository congestionRedisRepository;

    @Mock
    private CongestionJpaRepository congestionJpaRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CongestionService congestionService;

    private CongestionRedisDto createRedisDto(String areaCode, String level) {
        return new CongestionRedisDto(
                areaCode, level, "테스트 메시지",
                10000, 12000, "2026-04-01 14:00",
                List.of(new CongestionRedisDto.ForecastDto(
                        "2026-04-01 15:00", "보통", 8000, 10000
                ))
        );
    }

    private Congestion createEntity(String areaCode, CongestionLevel level) {
        return Congestion.builder()
                .areaCode(areaCode)
                .congestionLevel(level)
                .congestionMessage("테스트 메시지")
                .minPeopleCount(10000)
                .maxPeopleCount(12000)
                .populationTime(LocalDateTime.of(2026, 4, 1, 14, 0))
                .forecast("[]")
                .build();
    }

    @Nested
    @DisplayName("findByAreaCode")
    class FindByAreaCode {

        @Test
        @DisplayName("Redis 캐시 히트 → Redis 데이터 반환")
        void redisHit() {
            CongestionRedisDto dto = createRedisDto("POI001", "붐빔");
            given(congestionRedisRepository.findByAreaCode("POI001"))
                    .willReturn(Optional.of(dto));

            CongestionResponse result = congestionService.findByAreaCode("POI001");

            assertThat(result.areaCode()).isEqualTo("POI001");
            assertThat(result.congestionLevel()).isEqualTo("붐빔");
            assertThat(result.forecasts()).hasSize(1);
            then(congestionJpaRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Redis 미스 + DB 히트 → DB 데이터 반환")
        void redisMissDbHit() {
            given(congestionRedisRepository.findByAreaCode("POI001"))
                    .willReturn(Optional.empty());
            given(congestionJpaRepository.findTopByAreaCodeOrderByCreatedAtDesc("POI001"))
                    .willReturn(Optional.of(createEntity("POI001", CongestionLevel.BUSY)));

            CongestionResponse result = congestionService.findByAreaCode("POI001");

            assertThat(result.areaCode()).isEqualTo("POI001");
            assertThat(result.congestionLevel()).isEqualTo("붐빔");
        }

        @Test
        @DisplayName("Redis 미스 + DB 미스 → GlobalException(404)")
        void redisMissDbMiss() {
            given(congestionRedisRepository.findByAreaCode("POI999"))
                    .willReturn(Optional.empty());
            given(congestionJpaRepository.findTopByAreaCodeOrderByCreatedAtDesc("POI999"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> congestionService.findByAreaCode("POI999"))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getStatus()).isEqualTo(404));
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("Redis에 데이터 있음 → Redis 데이터 반환")
        void redisHasData() {
            List<CongestionRedisDto> dtos = List.of(
                    createRedisDto("POI001", "붐빔"),
                    createRedisDto("POI002", "여유")
            );
            given(congestionRedisRepository.findAll()).willReturn(dtos);

            List<CongestionResponse> result = congestionService.findAll();

            assertThat(result).hasSize(2);
            then(congestionJpaRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Redis 비어있음 → DB 폴백 조회")
        void redisMissFallbackToDb() {
            given(congestionRedisRepository.findAll()).willReturn(Collections.emptyList());
            given(congestionJpaRepository.findLatestPerLocation())
                    .willReturn(List.of(createEntity("POI001", CongestionLevel.NORMAL)));

            List<CongestionResponse> result = congestionService.findAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).congestionLevel()).isEqualTo("보통");
        }
    }

    @Nested
    @DisplayName("saveAllToRedis")
    class SaveAllToRedis {

        @Test
        @DisplayName("Redis 배치 저장 호출")
        void saveAll() {
            List<CongestionRedisDto> dtos = List.of(createRedisDto("POI001", "붐빔"));

            congestionService.saveAllToRedis(dtos);

            then(congestionRedisRepository).should().saveAll(dtos);
        }
    }

    @Nested
    @DisplayName("saveAllToDb")
    class SaveAllToDb {

        @Test
        @DisplayName("DTO → Entity 변환 후 DB 배치 저장")
        void saveAll() {
            List<CongestionRedisDto> dtos = List.of(createRedisDto("POI001", "붐빔"));

            congestionService.saveAllToDb(dtos);

            then(congestionJpaRepository).should().saveAll(anyList());
        }

        @Test
        @DisplayName("populationTime이 null이어도 저장 성공")
        void saveAllWithNullPopulationTime() {
            CongestionRedisDto dto = new CongestionRedisDto(
                    "POI001", "여유", "메시지", 1000, 2000, null, Collections.emptyList()
            );

            congestionService.saveAllToDb(List.of(dto));

            then(congestionJpaRepository).should().saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("deleteOlderThan")
    class DeleteOlderThan {

        @Test
        @DisplayName("cutoff 이전 데이터 삭제 → 삭제 건수 반환")
        void deleteOldData() {
            Instant cutoff = Instant.now();
            given(congestionJpaRepository.deleteByCreatedAtBefore(cutoff)).willReturn(5);

            int deleted = congestionService.deleteOlderThan(cutoff);

            assertThat(deleted).isEqualTo(5);
        }
    }
}
