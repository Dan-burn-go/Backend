package com.danburn.congestion.notification.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class NotificationFiredMarkerRepository {

    private static final String KEY_PREFIX = "notif:fired:";
    private static final Duration MARKER_TTL = Duration.ofSeconds(30);

    private final RedisTemplate<String, String> stringRedisTemplate;

    public void markFired(String areaCode) {
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + areaCode, "1", MARKER_TTL);
    }

    public boolean isRecentlyFired(String areaCode) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + areaCode));
    }
}
