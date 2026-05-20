package com.danburn.congestion.notification.service;

import com.danburn.congestion.notification.config.VapidProperties;
import com.danburn.congestion.notification.domain.NotificationSubscription;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.security.Security;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSender {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public enum SendResult {
        SUCCESS, DEAD, RETRY
    }

    private final VapidProperties vapidProperties;
    private PushService pushService;
    private boolean configured = false;

    public boolean isConfigured() {
        return configured;
    }

    @PostConstruct
    public void init() {
        if (vapidProperties.publicKey() == null || vapidProperties.publicKey().isBlank()
                || vapidProperties.privateKey() == null || vapidProperties.privateKey().isBlank()) {
            log.error("[WebPushSender] VAPID 키 미설정 - 알림 전송 불가. publicKey={}, privateKey={}",
                    vapidProperties.publicKey() == null ? "null" : "(blank)",
                    vapidProperties.privateKey() == null ? "null" : "(blank)");
            return;
        }
        try {
            pushService = new PushService(
                    vapidProperties.publicKey(),
                    vapidProperties.privateKey(),
                    vapidProperties.subject()
            );
            configured = true;
        } catch (Exception e) {
            log.error("[WebPushSender] PushService 초기화 실패 - VAPID 키 미설정 상태일 수 있음: {}", e.getMessage());
        }
    }

    public SendResult send(NotificationSubscription sub, String payload) {
        if (pushService == null) {
            log.warn("[WebPushSender] PushService 미초기화 - 알림 전송 건너뜀: endpoint={}", sub.getEndpoint());
            return SendResult.RETRY;
        }
        try {
            Subscription subscription = new Subscription(
                    sub.getEndpoint(),
                    new Subscription.Keys(sub.getP256dhKey(), sub.getAuthKey())
            );
            Notification notification = new Notification(subscription, payload);
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 201) {
                return SendResult.SUCCESS;
            } else if (statusCode == 404 || statusCode == 410) {
                log.info("[WebPushSender] 만료된 구독 감지 - status={}, endpoint={}", statusCode, sub.getEndpoint());
                return SendResult.DEAD;
            } else {
                log.warn("[WebPushSender] 알림 전송 실패 - status={}, endpoint={}", statusCode, sub.getEndpoint());
                return SendResult.RETRY;
            }
        } catch (Exception e) {
            log.warn("[WebPushSender] 알림 전송 중 예외 발생 - endpoint={}, error={}", sub.getEndpoint(), e.getMessage());
            return SendResult.RETRY;
        }
    }
}
