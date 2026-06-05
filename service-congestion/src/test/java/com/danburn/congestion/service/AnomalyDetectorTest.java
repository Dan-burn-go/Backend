package com.danburn.congestion.service;

import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.event.CongestionAnomalyEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectorTest {

    @Mock
    private HourlyAvgCacheService hourlyAvgCacheService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private Cursor<String> emptyCursor;

    @InjectMocks
    private AnomalyDetector anomalyDetector;

    private CongestionRedisDto busyDto(String areaCode, int maxPeople) {
        return new CongestionRedisDto(
                "명동", areaCode, "붐빔", "메시지",
                maxPeople - 2000, maxPeople,
                "2026-04-01 14:00", List.of()
        );
    }

    @Nested
    @DisplayName("detectAnomalies")
    class DetectAnomalies {

        @Test
        @DisplayName("빈 목록 → 빈 이벤트 목록")
        void emptyInput() {
            List<CongestionAnomalyEvent> result = anomalyDetector.detectAnomalies(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("baseline 없음 → fallback anomaly=true, tryArm 성공 시 이벤트 발행")
        void baselineMissingFallback() {
            CongestionRedisDto dto = busyDto("POI001", 30000);
            given(hourlyAvgCacheService.getAvgMax(eq("POI001"), any(int.class))).willReturn(Optional.empty());
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).hasSize(1);
            assertThat(events.get(0).areaCode()).isEqualTo("POI001");
            assertThat(events.get(0).ratio()).isNull();
        }

        @Test
        @DisplayName("ratio >= threshold → anomaly 이벤트 발행")
        void ratioExceedsThreshold() {
            CongestionRedisDto dto = busyDto("POI001", 30000);
            // avgMax=15000, ratio=2.0 >= 1.5
            given(hourlyAvgCacheService.getAvgMax(eq("POI001"), any(int.class))).willReturn(Optional.of(15000.0));
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).hasSize(1);
            assertThat(events.get(0).ratio()).isNotNull();
        }

        @Test
        @DisplayName("ratio < threshold 이나 절대 증가분 >= delta → anomaly 발행 (baseline 높은 지역)")
        void deltaExceedsThreshold() {
            CongestionRedisDto dto = busyDto("POI127", 20000);
            // avgMax=17000, ratio=1.18 < 1.5, delta=3000 >= 3000
            given(hourlyAvgCacheService.getAvgMax(eq("POI127"), any(int.class))).willReturn(Optional.of(17000.0));
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).hasSize(1);
            assertThat(events.get(0).ratio()).isNotNull();
        }

        @Test
        @DisplayName("ratio < threshold 이고 절대 증가분 < delta → anomaly 없음")
        void ratioAndDeltaBelowThreshold() {
            CongestionRedisDto dto = busyDto("POI127", 20000);
            // avgMax=18000, ratio=1.11 < 1.5, delta=2000 < 3000
            given(hourlyAvgCacheService.getAvgMax(eq("POI127"), any(int.class))).willReturn(Optional.of(18000.0));

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).isEmpty();
            // anomaly=false 로 tryArm 진입 전 단락 — NPE 마스킹이 아닌 실제 임계 판정 검증
            then(stringRedisTemplate).should(never()).opsForValue();
        }

        @Test
        @DisplayName("ratio < threshold → anomaly 없음")
        void ratioBelowThreshold() {
            CongestionRedisDto dto = busyDto("POI001", 15000);
            // avgMax=20000, ratio=0.75 < 1.5
            given(hourlyAvgCacheService.getAvgMax(eq("POI001"), any(int.class))).willReturn(Optional.of(20000.0));

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).isEmpty();
            // anomaly=false 로 tryArm 진입 전 단락 — NPE 마스킹이 아닌 실제 임계 판정 검증
            then(stringRedisTemplate).should(never()).opsForValue();
        }

        @Test
        @DisplayName("tryArm 실패(이미 armed) → 이벤트 미발행")
        void tryArmFails() {
            CongestionRedisDto dto = busyDto("POI001", 30000);
            given(hourlyAvgCacheService.getAvgMax(eq("POI001"), any(int.class))).willReturn(Optional.empty());
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(false);

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("avgMax=0 이고 current < deltaThreshold → anomaly 없음, ratio Infinity 미노출")
        void avgMaxZeroNoDivisionByZero() {
            CongestionRedisDto dto = busyDto("POI999", 1000);
            // avgMax=0.0, current=1000 < 3000 → anomaly false
            given(hourlyAvgCacheService.getAvgMax(eq("POI999"), any(int.class))).willReturn(Optional.of(0.0));

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).isEmpty();
            then(stringRedisTemplate).should(never()).opsForValue();
        }

        @Test
        @DisplayName("maxPeopleCount null → 해당 dto 건너뜀")
        void nullMaxPeopleCount() {
            CongestionRedisDto dto = new CongestionRedisDto(
                    "명동", "POI001", "붐빔", "메시지",
                    null, null, "2026-04-01 14:00", List.of()
            );

            List<CongestionAnomalyEvent> events = anomalyDetector.detectAnomalies(List.of(dto));

            assertThat(events).isEmpty();
        }
    }

    @Nested
    @DisplayName("releaseArmedForNonBusy")
    class ReleaseArmedForNonBusy {

        @Test
        @DisplayName("armed 키 없으면 Redis delete 미호출")
        void noArmedKeys() {
            given(stringRedisTemplate.scan(any(ScanOptions.class))).willReturn(emptyCursor);
            given(emptyCursor.hasNext()).willReturn(false);

            anomalyDetector.releaseArmedForNonBusy(List.of());

            then(stringRedisTemplate).should().scan(any(ScanOptions.class));
            then(stringRedisTemplate).shouldHaveNoMoreInteractions();
        }
    }
}
