package com.danburn.congestion.notification.service;

import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.notification.domain.NotificationSubscription;
import com.danburn.congestion.notification.dto.SubscribeRequest;
import com.danburn.congestion.notification.dto.SubscribeResult;
import com.danburn.congestion.notification.exception.AlreadyNotBusyException;
import com.danburn.congestion.notification.exception.AreaNotFoundException;
import com.danburn.congestion.notification.repository.NotificationFiredMarkerRepository;
import com.danburn.congestion.notification.repository.NotificationSubscriptionRepository;
import com.danburn.congestion.repository.CongestionRedisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Duration TTL = Duration.ofHours(12);

    private final NotificationSubscriptionRepository repository;
    private final CongestionRedisRepository congestionRedisRepository;
    private final WebPushSender webPushSender;
    private final ObjectMapper objectMapper;
    private final NotificationFiredMarkerRepository firedMarkerRepository;

    @Transactional
    public SubscribeResult subscribe(SubscribeRequest req) {
        CongestionRedisDto congestionData = congestionRedisRepository.findByAreaCode(req.areaCode())
                .orElseThrow(() -> new AreaNotFoundException(req.areaCode()));

        CongestionLevel level = CongestionLevel.fromDescription(congestionData.congestionLevel());
        if (level != CongestionLevel.BUSY) {
            throw new AlreadyNotBusyException(level);
        }

        if (firedMarkerRepository.isRecentlyFired(req.areaCode())) {
            throw new AlreadyNotBusyException(level);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(TTL);

        Optional<NotificationSubscription> existing =
                repository.findByEndpointAndAreaCode(req.endpoint(), req.areaCode());

        if (existing.isPresent()) {
            NotificationSubscription sub = existing.get();
            sub.renewExpiresAt(expiresAt);
            repository.save(sub);
            log.info("[SubscriptionService] 구독 갱신 - areaCode={}, endpoint={}", req.areaCode(), req.endpoint());
            return new SubscribeResult("RENEWED", expiresAt);
        }

        NotificationSubscription sub = NotificationSubscription.builder()
                .endpoint(req.endpoint())
                .p256dhKey(req.keys().p256dh())
                .authKey(req.keys().auth())
                .areaCode(req.areaCode())
                .expiresAt(expiresAt)
                .build();
        try {
            NotificationSubscription saved = repository.save(sub);
            log.info("[SubscriptionService] 새 구독 등록 - areaCode={}, endpoint={}", req.areaCode(), req.endpoint());
            return new SubscribeResult("SUBSCRIBED", saved.getExpiresAt());
        } catch (DataIntegrityViolationException e) {
            return repository.findByEndpointAndAreaCode(req.endpoint(), req.areaCode())
                    .map(found -> {
                        found.renewExpiresAt(expiresAt);
                        repository.save(found);
                        log.info("[SubscriptionService] 구독 충돌 후 갱신 - areaCode={}, endpoint={}", req.areaCode(), req.endpoint());
                        return new SubscribeResult("RENEWED", found.getExpiresAt());
                    })
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public void unsubscribe(String endpoint, String areaCode) {
        repository.deleteByEndpointAndAreaCode(endpoint, areaCode);
        log.info("[SubscriptionService] 구독 취소 - areaCode={}, endpoint={}", areaCode, endpoint);
    }

    @Async("notificationExecutor")
    public void notifyAndCleanup(String areaCode, String areaName) {
        firedMarkerRepository.markFired(areaCode);

        List<NotificationSubscription> subscriptions =
                repository.findByAreaCodeAndExpiresAtAfter(areaCode, Instant.now());

        log.info("[SubscriptionService] 알림 전송 시작 - areaCode={}, 구독자 수={}", areaCode, subscriptions.size());

        for (NotificationSubscription sub : subscriptions) {
            try {
                String payload = buildPayload(areaName, areaCode);
                WebPushSender.SendResult result = webPushSender.send(sub, payload);

                if (result == WebPushSender.SendResult.SUCCESS || result == WebPushSender.SendResult.DEAD) {
                    repository.deleteByEndpointAndAreaCode(sub.getEndpoint(), sub.getAreaCode());
                    log.info("[SubscriptionService] 알림 전송 후 구독 삭제 - result={}, endpoint={}", result, sub.getEndpoint());
                } else {
                    log.info("[SubscriptionService] 알림 재시도 예정 - endpoint={}", sub.getEndpoint());
                }
            } catch (Exception e) {
                log.warn("[SubscriptionService] 알림 전송 실패 - endpoint={}, error={}", sub.getEndpoint(), e.getMessage());
            }
        }
    }

    private String buildPayload(String areaName, String areaCode) throws JsonProcessingException {
        Map<String, String> payload = Map.of(
                "title", areaName + "이(가) 한가해졌어요!",
                "body", "지금 방문하기 좋은 시간이에요",
                "url", "/places/" + areaCode
        );
        return objectMapper.writeValueAsString(payload);
    }
}
