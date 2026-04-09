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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventJpaRepository eventJpaRepository;
    private final SeoulCultureInfoApiClient apiClient;

    @Transactional
    public void fetchAndSyncEvents() {
        int batchSize = 1000;
        int maxBatches = 5; // 임시 기본 값
        LocalDate today = LocalDate.now();

        log.info("서울시 문화행사 API 동기화 시작");
        
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
                    upsertEventBatch(rows, today);

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

    private void upsertEventBatch(List<SeoulCultureInfoApiResponse.Row> rows, LocalDate today) {
        int insertCount = 0;
        int updateCount = 0;

        for (SeoulCultureInfoApiResponse.Row row : rows) {
            try {
                LocalDate startDate = parseDate(row.startDate());
                LocalDate endDate = parseDate(row.endDate());

                if (startDate == null || endDate == null || endDate.isBefore(today)) continue;

                Optional<Event> existingEvent = eventJpaRepository.findByEventTitleAndPlaceAndStartDate(
                        row.title(), row.place(), startDate
                );

                if (existingEvent.isPresent()) {
                    Event event = existingEvent.get();
                    event.updateDetails(
                            row.description(), endDate, row.codename(), row.useFee(), 
                            row.inquiry(), row.orgLink(), row.mainImg(), 
                            row.latitude(), row.longitude()
                    );
                    updateCount++;
                } else {
                    Event newEvent = Event.builder()
                            .eventTitle(row.title())
                            .place(row.place())
                            .startDate(startDate)
                            .endDate(endDate)
                            .description(row.description())
                            .inquiry(row.inquiry())
                            .codename(row.codename())
                            .useFee(row.useFee())
                            .orgLink(row.orgLink())
                            .mainImg(row.mainImg())
                            .latitude(row.latitude())
                            .longitude(row.longitude())
                            .build();
                    eventJpaRepository.save(newEvent);
                    insertCount++;
                }
            } catch (Exception e) {
                log.warn("문화행사 데이터 동기화 실패 - 행사명: {}", row.title(), e);
            }
        }
        log.info("배치 처리 완료 - Insert: {}건, Update: {}건", insertCount, updateCount);
    }
    
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 10) return null;
        
        try {
            return LocalDate.parse(dateStr.substring(0, 10));
        } catch (DateTimeParseException e) {
            log.debug("날짜 파싱 실패 (데이터: {}): {}", dateStr, e.getMessage());
            return null;
        }
    }
}