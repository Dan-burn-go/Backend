package com.danburn.map.service;

import com.danburn.map.domain.Event;
import com.danburn.map.dto.request.SeoulCultureInfoApiRequest;
import com.danburn.map.dto.response.SeoulCultureInfoApiResponse;
import com.danburn.map.infra.SeoulCultureInfoApiClient;
import com.danburn.map.repository.EventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final SeoulCultureInfoApiClient apiClient;
    private final EventUpsertService eventUpsertService;
    private final EventJpaRepository eventJpaRepository;

    public void fetchAndSyncEvents() {
        int batchSize = 1000;
        int maxBatches = 5; // 임시 기본 값
        LocalDate today = LocalDate.now();

        log.info("서울시 문화행사 API 동기화 시작");

        List<Event> allEvents = eventJpaRepository.findAll();
        Map<String, Event> existingEventMap = new HashMap<>();
        for (Event e : allEvents) {
            String key = e.getEventTitle() + "|" + e.getPlace() + "|" + e.getStartDate();
            existingEventMap.putIfAbsent(key, e);
        }

        for (int i = 0; i < maxBatches; i++) {
            int startIndex = i * batchSize + 1;
            int endIndex = (i + 1) * batchSize;

            try {
                SeoulCultureInfoApiRequest request = SeoulCultureInfoApiRequest.builder()
                        .startIndex(startIndex)
                        .endIndex(endIndex)
                        .build();

                SeoulCultureInfoApiResponse response = apiClient.fetchEvents(request);

                if (response != null && response.culturalEventInfo() != null && response.culturalEventInfo().row() != null) {
                    if (i == 0 && response.culturalEventInfo().listTotalCount() != null) {
                        Integer listTotalCount = response.culturalEventInfo().listTotalCount();
                        maxBatches = (int)Math.ceil((double)listTotalCount / batchSize);
                    }
                    List<SeoulCultureInfoApiResponse.Row> rows = response.culturalEventInfo().row();
                    eventUpsertService.upsertEventBatch(rows, today, existingEventMap);

                    if (rows.size() < batchSize) break;
                } else {
                    break;
                }
            } catch (Exception e) {
                log.error("API 호출 또는 데이터 처리 중 오류 발생 (startIndex: {}, endIndex: {})", startIndex, endIndex, e);
            }
        }

        eventJpaRepository.deleteByEndDateBefore(today);
        log.info("문화행사 동기화 및 기간 만료 데이터 삭제 완료");
    }
}