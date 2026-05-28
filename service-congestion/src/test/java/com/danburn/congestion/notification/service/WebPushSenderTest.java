package com.danburn.congestion.notification.service;

import com.danburn.congestion.notification.config.VapidProperties;
import com.danburn.congestion.notification.domain.NotificationSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebPushSenderTest {

    private NotificationSubscription buildSub() {
        return NotificationSubscription.builder()
                .endpoint("https://fcm.example.com/endpoint")
                .p256dhKey("p256dhKey")
                .authKey("authKey")
                .areaCode("POI001")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Nested
    @DisplayName("init - VAPID 키 설정 여부에 따른 초기화")
    class Init {

        @Test
        @DisplayName("VAPID 키 미설정 → configured=false")
        void notConfiguredWhenKeysBlank() {
            VapidProperties props = new VapidProperties(null, null, "mailto:test@example.com");
            WebPushSender sender = new WebPushSender(props);
            sender.init();

            assertThat(sender.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("VAPID 키 빈 문자열 → configured=false")
        void notConfiguredWhenKeysEmpty() {
            VapidProperties props = new VapidProperties("", "", "mailto:test@example.com");
            WebPushSender sender = new WebPushSender(props);
            sender.init();

            assertThat(sender.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("VAPID 키 잘못된 형식 → init 예외 삼켜서 configured=false")
        void notConfiguredWhenKeysInvalid() {
            VapidProperties props = new VapidProperties("invalidPublicKey", "invalidPrivateKey", "mailto:test@example.com");
            WebPushSender sender = new WebPushSender(props);
            sender.init();

            assertThat(sender.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("send - PushService 미초기화 시")
    class Send {

        @Test
        @DisplayName("PushService null이면 RETRY 반환")
        void returnsRetryWhenNotInitialized() {
            VapidProperties props = new VapidProperties(null, null, "mailto:test@example.com");
            WebPushSender sender = new WebPushSender(props);
            sender.init(); // configured=false, pushService=null

            WebPushSender.SendResult result = sender.send(buildSub(), "{\"title\":\"test\"}");

            assertThat(result).isEqualTo(WebPushSender.SendResult.RETRY);
        }
    }
}
