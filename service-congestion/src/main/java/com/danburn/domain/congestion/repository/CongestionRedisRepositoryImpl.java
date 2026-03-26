package com.danburn.domain.congestion.repository;

import com.danburn.domain.congestion.dto.CongestionRedisDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class CongestionRedisRepositoryImpl implements CongestionRedisRepository {

    private final RedisTemplate<String, CongestionRedisDto> congestionRedisTemplate;

    private static final String KEY_PREFIX = "congestion:";
    private static final long TTL_MINUTES = 10;

    @Override
    public void save(CongestionRedisDto dto) {
        String key = KEY_PREFIX + dto.locationId();
        congestionRedisTemplate.opsForValue().set(key, dto, TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public Optional<CongestionRedisDto> findByLocationId(Long locationId) {
        String key = KEY_PREFIX + locationId;
        CongestionRedisDto dto = congestionRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(dto);
    }

    @Override
    public List<CongestionRedisDto> findAll() {
        Set<String> keys = congestionRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        return keys.stream()
                .map(key -> congestionRedisTemplate.opsForValue().get(key))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void delete(Long locationId) {
        String key = KEY_PREFIX + locationId;
        congestionRedisTemplate.delete(key);
    }
}
