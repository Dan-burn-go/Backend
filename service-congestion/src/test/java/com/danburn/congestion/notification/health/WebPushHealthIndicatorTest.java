package com.danburn.congestion.notification.health;

import com.danburn.congestion.notification.service.WebPushSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebPushHealthIndicatorTest {

    @Mock
    private WebPushSender webPushSender;

    @InjectMocks
    private WebPushHealthIndicator healthIndicator;

    @Nested
    @DisplayName("health")
    class HealthCheck {

        @Test
        @DisplayName("WebPushSender 설정됨 → UP")
        void upWhenConfigured() {
            given(webPushSender.isConfigured()).willReturn(true);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("WebPushSender 미설정 → DOWN + detail")
        void downWhenNotConfigured() {
            given(webPushSender.isConfigured()).willReturn(false);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("reason");
        }
    }
}
