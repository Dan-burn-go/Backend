package com.danburn.map.service;

import com.danburn.map.domain.Event;
import com.danburn.map.dto.response.CultureEventResponse;
import com.danburn.map.repository.EventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CultureEventService {

    private static final double RADIUS_METER = 1000.0;

    private final EventJpaRepository eventJpaRepository;

    @Transactional(readOnly = true)
    public List<CultureEventResponse> getCultureEvents(Double latitude, Double longitude) {
        log.debug("문화행사 조회 요청 - latitude: {}, longitude: {}", latitude, longitude);

        List<Event> events = eventJpaRepository.findEventsWithinRadius(latitude, longitude, RADIUS_METER);
        log.debug("{}km 이내 문화행사 {}건 조회 완료", (int)(RADIUS_METER / 1000), events.size());

        return events.stream()
                .map(this::toResponse)
                .toList();
    }

    private CultureEventResponse toResponse(Event event) {
        return new CultureEventResponse(
                event.getEventTitle(),
                event.getPlace(),
                event.getCodename(),
                event.getStartDate(),
                event.getEndDate(),
                event.getUseFee(),
                event.getOrgLink(),
                event.getMainImg(),
                event.getLatitude(),
                event.getLongitude()
        );
    }
}
