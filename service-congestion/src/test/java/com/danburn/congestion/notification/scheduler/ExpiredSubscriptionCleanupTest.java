package com.danburn.congestion.notification.scheduler;

import com.danburn.congestion.notification.repository.NotificationSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ExpiredSubscriptionCleanupTest {

    @Mock
    private NotificationSubscriptionRepository repository;

    @InjectMocks
    private ExpiredSubscriptionCleanup cleanup;

    @Nested
    @DisplayName("cleanupExpiredSubscriptions")
    class CleanupExpiredSubscriptions {

        @Test
        @DisplayName("만료된 구독 정리 → deleteByExpiresAtBefore 호출")
        void callsDeleteByExpiresAtBefore() {
            given(repository.deleteByExpiresAtBefore(any(Instant.class))).willReturn(5);

            cleanup.cleanupExpiredSubscriptions();

            then(repository).should().deleteByExpiresAtBefore(any(Instant.class));
        }

        @Test
        @DisplayName("삭제 0건이어도 정상 완료")
        void zeroDeletedIsOk() {
            given(repository.deleteByExpiresAtBefore(any(Instant.class))).willReturn(0);

            cleanup.cleanupExpiredSubscriptions();

            then(repository).should().deleteByExpiresAtBefore(any(Instant.class));
        }

        @Test
        @DisplayName("예외 발생해도 전파되지 않음")
        void exceptionIsSwallowed() {
            given(repository.deleteByExpiresAtBefore(any(Instant.class)))
                    .willThrow(new RuntimeException("DB 오류"));

            // 예외가 전파되지 않아야 함
            cleanup.cleanupExpiredSubscriptions();

            then(repository).should().deleteByExpiresAtBefore(any(Instant.class));
        }
    }
}
