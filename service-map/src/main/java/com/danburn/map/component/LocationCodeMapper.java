package com.danburn.map.component;

import com.danburn.map.domain.Location;
import com.danburn.map.repository.LocationJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationCodeMapper {

    private final LocationJpaRepository locationJpaRepository;
    private Map<String, Long> areaCodeToIdMap = new ConcurrentHashMap<>();

    // 스프링 빈 초기화 후 자동으로 1회 실행되어 DB의 데이터를 메모리에 로드
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

    // 외부 서비스(Service)에서 호출할 매핑 메서드
    public Long getLocationIdByAreaCode(String apiAreaCode) {
        return areaCodeToIdMap.get(apiAreaCode);
    }
}