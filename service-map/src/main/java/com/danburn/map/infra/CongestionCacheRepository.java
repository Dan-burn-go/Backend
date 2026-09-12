package com.danburn.map.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CongestionCacheRepository {

    private static final String KEY_PREFIX = "map:congestion:";
    // 서킷 브레이커 fallback용 최근 혼잡도 보존 시간
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public void save(String areaCode, String congestionLevel) {
        redisTemplate.opsForValue().set(KEY_PREFIX + areaCode, congestionLevel, TTL);
    }

    public Optional<String> find(String areaCode) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + areaCode));
    }
}
