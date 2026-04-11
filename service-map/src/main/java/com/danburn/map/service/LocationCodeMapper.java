package com.danburn.map.service;

import com.danburn.map.domain.Location;
import com.danburn.map.repository.LocationJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationCodeMapper {

    private final LocationJpaRepository locationJpaRepository;
    private Map<String, Long> areaCodeToIdMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("로컬 캐시에 Location Area Code 맵핑 정보를 로드합니다...");
        areaCodeToIdMap = locationJpaRepository.findAll().stream()
                .collect(Collectors.toConcurrentMap(
                        Location::getApiAreaCode,
                        Location::getLocationId
                ));
        log.info("총 {}개의 Location Area Code가 로드되었습니다.", areaCodeToIdMap.size());
    }

    public Optional<Long> getLocationIdByAreaCode(String apiAreaCode) {
        return Optional.ofNullable(areaCodeToIdMap.get(apiAreaCode));
    }
}