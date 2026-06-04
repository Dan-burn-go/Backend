package com.danburn.congestion.repository;

import com.danburn.congestion.dto.CongestionRedisDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
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

    private static final String KEY_PREFIX = "congestion:dto:";
    private static final String AREA_CODES_SET_KEY = "congestion:area_codes";
    private static final long TTL_MINUTES = 15;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addAreaCode(String areaCode) {
        ((SetOperations) congestionRedisTemplate.opsForSet()).add(AREA_CODES_SET_KEY, areaCode);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void removeAreaCode(String areaCode) {
        ((SetOperations) congestionRedisTemplate.opsForSet()).remove(AREA_CODES_SET_KEY, areaCode);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Set<String> getAreaCodes() {
        return ((SetOperations) congestionRedisTemplate.opsForSet()).members(AREA_CODES_SET_KEY);
    }

    @Override
    public void save(CongestionRedisDto dto) {
        String key = KEY_PREFIX + dto.areaCode();
        congestionRedisTemplate.opsForValue().set(key, dto, TTL_MINUTES, TimeUnit.MINUTES);
        addAreaCode(dto.areaCode());
    }

    @Override
    public void saveAll(List<CongestionRedisDto> dtos) {
        congestionRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                RedisOperations<String, CongestionRedisDto> ops =
                        (RedisOperations<String, CongestionRedisDto>) operations;
                for (CongestionRedisDto dto : dtos) {
                    String key = KEY_PREFIX + dto.areaCode();
                    ops.opsForValue().set(key, dto, TTL_MINUTES, TimeUnit.MINUTES);
                    ((SetOperations) ops.opsForSet()).add(AREA_CODES_SET_KEY, dto.areaCode());
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
        Set<String> areaCodes = getAreaCodes();
        if (areaCodes == null || areaCodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = areaCodes.stream().map(code -> KEY_PREFIX + code).toList();

        List<CongestionRedisDto> values = congestionRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<CongestionRedisDto> findAllByAreaCodes(List<String> areaCodes) {
        List<String> keys = areaCodes.stream()
                .map(code -> KEY_PREFIX + code)
                .toList();
        List<CongestionRedisDto> values = congestionRedisTemplate.opsForValue().multiGet(keys);
        return values != null ? values : Collections.nCopies(areaCodes.size(), null);
    }

    @Override
    public void delete(String areaCode) {
        String key = KEY_PREFIX + areaCode;
        congestionRedisTemplate.delete(key);
        removeAreaCode(areaCode);
    }
}
