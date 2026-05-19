package com.danburn.congestion.notification.scheduler;

import com.danburn.congestion.notification.repository.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredSubscriptionCleanup {

    private final NotificationSubscriptionRepository repository;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredSubscriptions() {
        log.info("[ExpiredSubscriptionCleanup] 만료된 구독 정리 시작");
        try {
            int deleted = repository.deleteByExpiresAtBefore(Instant.now());
            log.info("[ExpiredSubscriptionCleanup] 만료된 구독 정리 완료 - {}건 삭제", deleted);
        } catch (Exception e) {
            log.error("[ExpiredSubscriptionCleanup] 만료된 구독 정리 실패 - {}", e.getMessage(), e);
        }
    }
}
