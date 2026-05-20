package com.danburn.congestion.notification.health;

import com.danburn.congestion.notification.service.WebPushSender;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebPushHealthIndicator implements HealthIndicator {

    private final WebPushSender webPushSender;

    @Override
    public Health health() {
        if (!webPushSender.isConfigured()) {
            return Health.down()
                    .withDetail("reason", "VAPID keys not configured")
                    .build();
        }
        return Health.up().build();
    }
}
