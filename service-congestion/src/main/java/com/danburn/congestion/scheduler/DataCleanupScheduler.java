package com.danburn.congestion.scheduler;

import com.danburn.congestion.service.CongestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {

    private final CongestionService congestionService;

    @Value("${congestion.cleanup.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${congestion.cleanup.cron:0 0 3 * * *}")
    public void cleanupOldData() {
        log.info("[DataCleanupScheduler] 오래된 혼잡도 데이터 정리 시작");
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int deletedCount = congestionService.deleteOlderThan(cutoff);
            log.info("[DataCleanupScheduler] 데이터 정리 완료 - {}건 삭제 (기준: {}일 이전)", deletedCount, retentionDays);
        } catch (Exception e) {
            log.error("[DataCleanupScheduler] 데이터 정리 실패 - {}", e.getMessage(), e);
        }
    }
}
