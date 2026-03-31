package com.danburn.congestion.repository;

import com.danburn.congestion.dto.CongestionRedisDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Repository;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.Collections;
import java.util.HashSet;
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
    private static final long TTL_MINUTES = 15;

    @Override
    public void save(CongestionRedisDto dto) {
        String key = KEY_PREFIX + dto.areaCode();
        congestionRedisTemplate.opsForValue().set(key, dto, TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void saveAll(List<CongestionRedisDto> dtos) {
        congestionRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) {
                RedisOperations<String, CongestionRedisDto> ops =
                        (RedisOperations<String, CongestionRedisDto>) operations;
                for (CongestionRedisDto dto : dtos) {
                    String key = KEY_PREFIX + dto.areaCode();
                    ops.opsForValue().set(key, dto, TTL_MINUTES, TimeUnit.MINUTES);
                }
                return null;
            }
        });
    }

    @Override
    public Optional<CongestionRedisDto> findByAreaCode(String areaCode) {
        String key = KEY_PREFIX + areaCode;
        CongestionRedisDto dto = congestionRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(dto);
    }

    @Override
    public List<CongestionRedisDto> findAll() {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(1000).build();
        try (Cursor<String> cursor = congestionRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }

        if (keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<CongestionRedisDto> values = congestionRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void delete(String areaCode) {
        String key = KEY_PREFIX + areaCode;
        congestionRedisTemplate.delete(key);
    }
}
