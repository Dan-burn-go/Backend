package com.danburn.congestion.service;

import com.danburn.congestion.dto.response.CongestionTrendResponse;
import com.danburn.congestion.dto.response.CongestionTrendResponse.TrendData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class HourlyAvgCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private CongestionAnalysisService analysisService;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private HourlyAvgCacheService hourlyAvgCacheService;

    private CongestionTrendResponse trendWith(int hour, double avgMax) {
        TrendData data = new TrendData(hour, String.valueOf(hour) + "시", "보통", 10000.0, avgMax, 5L);
        return new CongestionTrendResponse("POI001", "명동", List.of(data));
    }

    @Nested
    @DisplayName("getAvgMax")
    class GetAvgMax {

        @Test
        @DisplayName("Redis 캐시 히트 → DB 미조회")
        void cacheHit() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("congestion:hourly-avg:POI001:14")).willReturn("20000.0");

            Optional<Double> result = hourlyAvgCacheService.getAvgMax("POI001", 14);

            assertThat(result).contains(20000.0);
            then(analysisService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Redis 미스 + DB 데이터 있음 → DB 조회 후 캐시 저장")
        void cacheMissDbHit() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(anyString())).willReturn(null);
            given(analysisService.getHourlyTrend("POI001", 7)).willReturn(trendWith(14, 18000.0));

            Optional<Double> result = hourlyAvgCacheService.getAvgMax("POI001", 14);

            assertThat(result).contains(18000.0);
            then(valueOps).should().set(eq("congestion:hourly-avg:POI001:14"), eq("18000.0"), any(Duration.class));
        }

        @Test
        @DisplayName("Redis 미스 + DB 해당 시간 없음 → Optional.empty")
        void cacheMissDbMissHour() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(anyString())).willReturn(null);
            given(analysisService.getHourlyTrend("POI001", 7)).willReturn(trendWith(10, 18000.0));

            Optional<Double> result = hourlyAvgCacheService.getAvgMax("POI001", 14);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Redis 캐시 파싱 오류 → DB 폴백")
        void cacheParseError() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(anyString())).willReturn("not-a-number");
            given(analysisService.getHourlyTrend("POI001", 7)).willReturn(trendWith(14, 15000.0));

            Optional<Double> result = hourlyAvgCacheService.getAvgMax("POI001", 14);

            assertThat(result).contains(15000.0);
        }

        @Test
        @DisplayName("DB 조회 예외 → Optional.empty")
        void dbException() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(anyString())).willReturn(null);
            given(analysisService.getHourlyTrend("POI001", 7)).willThrow(new RuntimeException("DB 오류"));

            Optional<Double> result = hourlyAvgCacheService.getAvgMax("POI001", 14);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("avgMaxPeople가 0이면 결과에서 제외")
        void zeroAvgExcluded() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(anyString())).willReturn(null);
            TrendData data = new TrendData(14, "14시", "보통", 0.0, 0.0, 3L);
            CongestionTrendResponse trend = new CongestionTrendResponse("POI001", "명동", List.of(data));
            given(analysisService.getHourlyTrend("POI001", 7)).willReturn(trend);

            Optional<Double> result = hourlyAvgCacheService.getAvgMax("POI001", 14);

            assertThat(result).isEmpty();
        }
    }
}
