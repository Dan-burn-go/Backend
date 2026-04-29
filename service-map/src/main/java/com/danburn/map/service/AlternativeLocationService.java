package com.danburn.map.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.map.domain.AlternativeLocation;
import com.danburn.map.dto.response.AlternativeLocationResponse;
import com.danburn.map.infra.CongestionApiClient;
import com.danburn.map.repository.AlternativeLocationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlternativeLocationService {

    private static final Map<String, Integer> CONGESTION_ORDER = Map.of(
            "여유", 1,
            "보통", 2,
            "약간 붐빔", 3,
            "붐빔", 4
    );

    private final LocationCodeMapper locationCodeMapper;
    private final AlternativeLocationJpaRepository alternativeLocationJpaRepository;
    private final CongestionApiClient congestionApiClient;

    @Transactional(readOnly = true)
    public List<AlternativeLocationResponse> getAlternativeLocations(String areaCode) {
        Long locationId = locationCodeMapper.getLocationIdByAreaCode(areaCode)
                .orElseThrow(() -> new GlobalException(404, "존재하지 않는 지역 코드입니다: " + areaCode));

        List<AlternativeLocation> alternatives = alternativeLocationJpaRepository
                .findAlternativeLocationIdOrderByPriority(locationId);

        List<AlternativeLocationResponse> responses = Flux.fromIterable(alternatives)
                .flatMap(alt -> {
                    String altAreaCode = alt.getAlternativeLocation().getApiAreaCode();
                    String locationName = alt.getAlternativeLocation().getLocationName();
                    Double latitude = alt.getAlternativeLocation().getLatitude();
                    Double longitude = alt.getAlternativeLocation().getLongitude();
                    Integer priority = alt.getPriority();

                    return congestionApiClient.getCongestionLevel(altAreaCode)
                            .map(congestionLevel -> new AlternativeLocationResponse(
                                    altAreaCode, locationName, latitude, longitude, priority, congestionLevel
                            ))
                            .switchIfEmpty(Mono.fromSupplier(() -> new AlternativeLocationResponse(
                                    altAreaCode, locationName, latitude, longitude, priority, null
                            )));
                })
                .collectList()
                .block();

        return responses.stream()
                .sorted(Comparator
                        .comparingInt((AlternativeLocationResponse r) ->
                                CONGESTION_ORDER.getOrDefault(r.congestionLevel(), Integer.MAX_VALUE))
                        .thenComparingInt(AlternativeLocationResponse::priority))
                .toList();
    }
}
