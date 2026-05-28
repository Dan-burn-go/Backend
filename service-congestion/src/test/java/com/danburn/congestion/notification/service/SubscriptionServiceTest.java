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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private NotificationSubscriptionRepository repository;

    @Mock
    private CongestionRedisRepository congestionRedisRepository;

    @Mock
    private WebPushSender webPushSender;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NotificationFiredMarkerRepository firedMarkerRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private CongestionRedisDto busyDto(String areaCode) {
        return new CongestionRedisDto(
                "명동", areaCode, "붐빔", "붐빔 메시지",
                30000, 34000, "2026-04-01 14:00", List.of()
        );
    }

    private SubscribeRequest subscribeRequest(String areaCode) {
        return new SubscribeRequest(
                "https://fcm.example.com/endpoint",
                new SubscribeRequest.Keys("p256dhKey", "authKey"),
                areaCode
        );
    }

    private NotificationSubscription buildSub(String endpoint, String areaCode) {
        return NotificationSubscription.builder()
                .endpoint(endpoint)
                .p256dhKey("p256dhKey")
                .authKey("authKey")
                .areaCode(areaCode)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("areaCode가 Redis에 없으면 AreaNotFoundException")
        void areaNotFound() {
            given(congestionRedisRepository.findByAreaCode("POI999")).willReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.subscribe(subscribeRequest("POI999")))
                    .isInstanceOf(AreaNotFoundException.class);
        }

        @Test
        @DisplayName("혼잡도가 BUSY가 아니면 AlreadyNotBusyException")
        void notBusy() {
            CongestionRedisDto normalDto = new CongestionRedisDto(
                    "명동", "POI001", "보통", "메시지", 10000, 12000, "2026-04-01 14:00", List.of()
            );
            given(congestionRedisRepository.findByAreaCode("POI001")).willReturn(Optional.of(normalDto));

            assertThatThrownBy(() -> subscriptionService.subscribe(subscribeRequest("POI001")))
                    .isInstanceOf(AlreadyNotBusyException.class);
        }

        @Test
        @DisplayName("최근 알림 발송된 경우 AlreadyNotBusyException")
        void recentlyFired() {
            given(congestionRedisRepository.findByAreaCode("POI001")).willReturn(Optional.of(busyDto("POI001")));
            given(firedMarkerRepository.isRecentlyFired("POI001")).willReturn(true);

            assertThatThrownBy(() -> subscriptionService.subscribe(subscribeRequest("POI001")))
                    .isInstanceOf(AlreadyNotBusyException.class);
        }

        @Test
        @DisplayName("기존 구독 없음 → 새 구독 SUBSCRIBED 반환")
        void newSubscription() {
            SubscribeRequest req = subscribeRequest("POI001");
            NotificationSubscription saved = buildSub(req.endpoint(), "POI001");

            given(congestionRedisRepository.findByAreaCode("POI001")).willReturn(Optional.of(busyDto("POI001")));
            given(firedMarkerRepository.isRecentlyFired("POI001")).willReturn(false);
            given(repository.findByEndpointAndAreaCode(req.endpoint(), "POI001")).willReturn(Optional.empty());
            given(repository.save(any())).willReturn(saved);

            SubscribeResult result = subscriptionService.subscribe(req);

            assertThat(result.status()).isEqualTo("SUBSCRIBED");
        }

        @Test
        @DisplayName("기존 구독 있음 → 갱신 RENEWED 반환")
        void renewSubscription() {
            SubscribeRequest req = subscribeRequest("POI001");
            NotificationSubscription existing = buildSub(req.endpoint(), "POI001");

            given(congestionRedisRepository.findByAreaCode("POI001")).willReturn(Optional.of(busyDto("POI001")));
            given(firedMarkerRepository.isRecentlyFired("POI001")).willReturn(false);
            given(repository.findByEndpointAndAreaCode(req.endpoint(), "POI001")).willReturn(Optional.of(existing));
            given(repository.save(existing)).willReturn(existing);

            SubscribeResult result = subscriptionService.subscribe(req);

            assertThat(result.status()).isEqualTo("RENEWED");
        }

        @Test
        @DisplayName("저장 중 DataIntegrityViolationException → 재조회 후 RENEWED 반환")
        void conflictOnSave() {
            SubscribeRequest req = subscribeRequest("POI001");
            NotificationSubscription conflicted = buildSub(req.endpoint(), "POI001");

            given(congestionRedisRepository.findByAreaCode("POI001")).willReturn(Optional.of(busyDto("POI001")));
            given(firedMarkerRepository.isRecentlyFired("POI001")).willReturn(false);
            given(repository.findByEndpointAndAreaCode(req.endpoint(), "POI001"))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(conflicted));
            given(repository.save(any()))
                    .willThrow(new DataIntegrityViolationException("uk violation"))
                    .willReturn(conflicted);

            SubscribeResult result = subscriptionService.subscribe(req);

            assertThat(result.status()).isEqualTo("RENEWED");
        }
    }

    @Nested
    @DisplayName("unsubscribe")
    class Unsubscribe {

        @Test
        @DisplayName("구독 취소 → deleteByEndpointAndAreaCode 호출")
        void unsubscribe() {
            subscriptionService.unsubscribe("https://fcm.example.com/endpoint", "POI001");

            then(repository).should().deleteByEndpointAndAreaCode("https://fcm.example.com/endpoint", "POI001");
        }
    }

    @Nested
    @DisplayName("notifyAndCleanup")
    class NotifyAndCleanup {

        @Test
        @DisplayName("구독자 없으면 전송 없음")
        void noSubscribers() {
            given(repository.findByAreaCodeAndExpiresAtAfter(anyString(), any())).willReturn(List.of());

            subscriptionService.notifyAndCleanup("POI001", "명동");

            then(webPushSender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("전송 성공(SUCCESS) → 구독 삭제")
        void sendSuccess() {
            NotificationSubscription sub = buildSub("https://fcm.example.com/ep", "POI001");
            given(repository.findByAreaCodeAndExpiresAtAfter(anyString(), any())).willReturn(List.of(sub));
            given(webPushSender.send(any(), anyString())).willReturn(WebPushSender.SendResult.SUCCESS);

            subscriptionService.notifyAndCleanup("POI001", "명동");

            then(repository).should().deleteByEndpointAndAreaCode(sub.getEndpoint(), sub.getAreaCode());
        }

        @Test
        @DisplayName("전송 만료(DEAD) → 구독 삭제")
        void sendDead() {
            NotificationSubscription sub = buildSub("https://fcm.example.com/ep", "POI001");
            given(repository.findByAreaCodeAndExpiresAtAfter(anyString(), any())).willReturn(List.of(sub));
            given(webPushSender.send(any(), anyString())).willReturn(WebPushSender.SendResult.DEAD);

            subscriptionService.notifyAndCleanup("POI001", "명동");

            then(repository).should().deleteByEndpointAndAreaCode(sub.getEndpoint(), sub.getAreaCode());
        }

        @Test
        @DisplayName("전송 재시도(RETRY) → 구독 삭제 안 함")
        void sendRetry() {
            NotificationSubscription sub = buildSub("https://fcm.example.com/ep", "POI001");
            given(repository.findByAreaCodeAndExpiresAtAfter(anyString(), any())).willReturn(List.of(sub));
            given(webPushSender.send(any(), anyString())).willReturn(WebPushSender.SendResult.RETRY);

            subscriptionService.notifyAndCleanup("POI001", "명동");

            then(repository).should(never()).deleteByEndpointAndAreaCode(anyString(), anyString());
        }
    }
}
