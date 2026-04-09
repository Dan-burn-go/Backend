package com.danburn.map.service;

import com.danburn.map.domain.Event;
import com.danburn.map.dto.response.SeoulCultureInfoApiResponse;
import com.danburn.map.repository.EventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventUpsertService {
  private final EventJpaRepository eventJpaRepository;

  @Transactional
  public void upsertEventBatch(List<SeoulCultureInfoApiResponse.Row> rows, LocalDate today) {
    int insertCount = 0;
    int updateCount = 0;

    Map<String, Event> existingEventMap = new java.util.HashMap<>();
    List<Event> allEvents = eventJpaRepository.findAll();
    for (Event e : allEvents) {
      String key = e.getEventTitle() + "|" + e.getPlace() + "|" + e.getStartDate();
      existingEventMap.putIfAbsent(key, e);
    }

    for (SeoulCultureInfoApiResponse.Row row : rows) {
      try {
        LocalDate startDate = parseDate(row.startDate());
        LocalDate endDate = parseDate(row.endDate());

        if (startDate == null || endDate == null || endDate.isBefore(today)) continue;

        String mapKey = row.title() + "|" + row.place() + "|" + startDate;
        Event event = existingEventMap.get(mapKey);

        if (event != null) {
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

          existingEventMap.put(mapKey, newEvent);
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
