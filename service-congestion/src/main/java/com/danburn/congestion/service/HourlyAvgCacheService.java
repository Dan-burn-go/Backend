package com.danburn.congestion.service;

import com.danburn.congestion.dto.response.CongestionTrendResponse;
import com.danburn.congestion.dto.response.CongestionTrendResponse.TrendData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 시간대별 avgMaxPeople baseline 캐싱 (Redis, TTL 1h)
 *
 * key: congestion:hourly-avg:{areaCode}:{hour}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HourlyAvgCacheService {

    private static final String KEY_PREFIX = "congestion:hourly-avg:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final int LOOKBACK_DAYS = 7;

    private final StringRedisTemplate stringRedisTemplate;
    private final CongestionAnalysisService analysisService;

    public Optional<Double> getAvgMax(String areaCode, int hour) {
        String key = KEY_PREFIX + areaCode + ":" + hour;
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Optional.of(Double.parseDouble(cached));
            }
        } catch (NumberFormatException e) {
            log.warn("[HourlyAvgCache] 캐시 파싱 실패 - key={}, reason={}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("[HourlyAvgCache] 캐시 조회 실패 - key={}, reason={}", key, e.getMessage());
        }

        Optional<Double> avgMax = loadAvgMaxFromDb(areaCode, hour);
        avgMax.ifPresent(value -> writeCache(key, value));
        return avgMax;
    }

    private Optional<Double> loadAvgMaxFromDb(String areaCode, int hour) {
        try {
            CongestionTrendResponse trend = analysisService.getHourlyTrend(areaCode, LOOKBACK_DAYS);
            return trend.data().stream()
                    .filter(d -> d.key() == hour)
                    .map(TrendData::avgMaxPeople)
                    .filter(v -> v > 0)
                    .findFirst();
        } catch (Exception e) {
            log.warn("[HourlyAvgCache] DB baseline 조회 실패 - areaCode={}, hour={}, reason={}",
                    areaCode, hour, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(String key, double value) {
        try {
            stringRedisTemplate.opsForValue().set(key, Double.toString(value), TTL);
        } catch (Exception e) {
            log.warn("[HourlyAvgCache] 캐시 저장 실패 - key={}, reason={}", key, e.getMessage());
        }
    }
}
