package com.danburn.congestion.service;

import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.event.CongestionAnomalyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BUSY 구간 중 current/avg 비율 임계 초과 또는 절대 증가분 임계 초과 anomaly 감지.
 *
 * - baseline 높은 지역(관광지·체육시설)은 비율로 묻히므로 절대 증가분 OR 조건 병행
 *
 * - rising-edge 1회 발행: Redis SETNX armed 플래그(`congestion:anomaly-armed:{areaCode}`, TTL 24h)
 * - BUSY 해제 시 armed 플래그 명시 삭제 (BUSY 재진입 시 재발행 허용)
 * - baseline 부재 시 보수적으로 anomaly=true fallback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyDetector {

    private static final String ARMED_KEY_PREFIX = "congestion:anomaly-armed:";
    private static final Duration ARMED_TTL = Duration.ofHours(24);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HourlyAvgCacheService hourlyAvgCacheService;
    private final StringRedisTemplate stringRedisTemplate;

    // 필드 초기값 = @Value 기본값. Spring 미기동 단위 테스트에서도 실제 임계값으로 동작.
    @Value("${congestion.anomaly.max-people-ratio:1.5}")
    private double ratioThreshold = 1.5;

    @Value("${congestion.anomaly.min-people-delta:3000}")
    private double deltaThreshold = 3000;

    public List<CongestionAnomalyEvent> detectAnomalies(List<CongestionRedisDto> busyDtos) {
        if (busyDtos.isEmpty()) {
            return List.of();
        }
        int hour = LocalDateTime.now(KST).getHour();
        List<CongestionAnomalyEvent> events = new ArrayList<>();

        for (CongestionRedisDto dto : busyDtos) {
            String areaCode = dto.areaCode();
            Integer current = dto.maxPeopleCount();
            if (current == null) {
                continue;
            }

            Optional<Double> avgOpt = hourlyAvgCacheService.getAvgMax(areaCode, hour);
            double avgMax = avgOpt.orElse(0.0);

            boolean anomaly;
            double ratio;
            if (avgOpt.isEmpty()) {
                anomaly = true;
                ratio = -1.0;
                log.info("[AnomalyDetector] baseline 부재 fallback - areaCode={}", areaCode);
            } else if (avgMax <= 0.0) {
                // avgMax=0이면 ratio 계산 불가(Infinity 방지) → delta 조건만 판정
                ratio = 0.0;
                double delta = current.doubleValue();
                anomaly = delta >= deltaThreshold;
            } else {
                ratio = current.doubleValue() / avgMax;
                double delta = current.doubleValue() - avgMax;
                anomaly = ratio >= ratioThreshold || delta >= deltaThreshold;
            }

            if (!anomaly) {
                continue;
            }

            if (!tryArm(areaCode)) {
                continue;
            }

            events.add(new CongestionAnomalyEvent(
                    dto.areaName(),
                    areaCode,
                    dto.congestionLevel(),
                    current,
                    roundOne(avgMax),
                    ratio < 0 ? null : roundTwo(ratio),
                    dto.populationTime()
            ));
        }
        return events;
    }

    public void releaseArmedForNonBusy(List<CongestionRedisDto> currentDtos) {
        Set<String> armedKeys = scanArmedKeys();
        if (armedKeys.isEmpty()) {
            return;
        }
        Set<String> currentBusyCodes = new HashSet<>();
        for (CongestionRedisDto dto : currentDtos) {
            if (dto.congestionLevel() == null) {
                continue;
            }
            try {
                if (CongestionLevel.fromDescription(dto.congestionLevel()) == CongestionLevel.BUSY) {
                    currentBusyCodes.add(dto.areaCode());
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<String> toDelete = new ArrayList<>();
        for (String key : armedKeys) {
            String areaCode = key.substring(ARMED_KEY_PREFIX.length());
            if (!currentBusyCodes.contains(areaCode)) {
                toDelete.add(key);
            }
        }
        if (!toDelete.isEmpty()) {
            stringRedisTemplate.delete(toDelete);
            log.info("[AnomalyDetector] armed 플래그 해제 - {}건", toDelete.size());
        }
    }

    private boolean tryArm(String areaCode) {
        String key = ARMED_KEY_PREFIX + areaCode;
        try {
            Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ARMED_TTL);
            return Boolean.TRUE.equals(set);
        } catch (Exception e) {
            log.warn("[AnomalyDetector] armed 마킹 실패 - areaCode={}, reason={}", areaCode, e.getMessage());
            return false;
        }
    }

    private Set<String> scanArmedKeys() {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(ARMED_KEY_PREFIX + "*").count(1000).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("[AnomalyDetector] armed 키 스캔 실패 - reason={}", e.getMessage());
        }
        return keys;
    }

    private static double roundOne(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double roundTwo(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
