package com.danburn.map.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.map.domain.AlternativeLocation;
import com.danburn.map.dto.response.AlternativeLocationResponse;
import com.danburn.map.repository.AlternativeLocationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlternativeLocationService {

    private final LocationCodeMapper locationCodeMapper;
    private final AlternativeLocationJpaRepository alternativeLocationJpaRepository;

    @Transactional(readOnly = true)
    public List<AlternativeLocationResponse> getAlternativeLocations(String areaCode) {
        Long locationId = locationCodeMapper.getLocationIdByAreaCode(areaCode)
                .orElseThrow(() -> new GlobalException(404, "존재하지 않는 지역 코드입니다: " + areaCode));

        List<AlternativeLocation> alternatives = alternativeLocationJpaRepository
                .findAlternativeLocationIdOrderByPriority(locationId);

        return alternatives.stream()
                .map(alt -> new AlternativeLocationResponse(
                        alt.getAlternativeLocation().getApiAreaCode(),
                        alt.getAlternativeLocation().getLocationName(),
                        alt.getAlternativeLocation().getLatitude(),
                        alt.getAlternativeLocation().getLongitude(),
                        alt.getPriority(),
                        null
                ))
                .toList();
    }
}
