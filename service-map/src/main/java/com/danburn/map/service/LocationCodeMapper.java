package com.danburn.map.service;

import com.danburn.map.domain.Location;
import com.danburn.map.repository.LocationJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationCodeMapper {

    public record Coordinate(double latitude, double longitude) {}

    private final LocationJpaRepository locationJpaRepository;
    private Map<String, Long> areaCodeToIdMap;
    private Map<String, Coordinate> coordinateMap;

    @PostConstruct
    public void init() {
        log.info("로컬 캐시에 Location Area Code 맵핑 정보를 로드합니다...");
        List<Location> locations = locationJpaRepository.findAll();
        areaCodeToIdMap = locations.stream()
                .collect(Collectors.toConcurrentMap(Location::getApiAreaCode, Location::getLocationId));
        coordinateMap = locations.stream()
                .filter(l -> l.getLatitude() != null && l.getLongitude() != null)
                .collect(Collectors.toConcurrentMap(
                        Location::getApiAreaCode,
                        l -> new Coordinate(l.getLatitude(), l.getLongitude())
                ));
        log.info("총 {}개의 Location Area Code가 로드되었습니다.", areaCodeToIdMap.size());
    }

    public Optional<Long> getLocationIdByAreaCode(String apiAreaCode) {
        return Optional.ofNullable(areaCodeToIdMap.get(apiAreaCode));
    }

    public Optional<Coordinate> getCoordinateByAreaCode(String apiAreaCode) {
        return Optional.ofNullable(coordinateMap.get(apiAreaCode));
    }
}