package com.danburn.map.service;

import com.danburn.map.domain.Event;
import com.danburn.map.dto.response.SeoulCultureInfoApiResponse;
import com.danburn.map.repository.EventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EventUpsertServiceTest {

    @Mock
    private EventJpaRepository eventJpaRepository;

    @InjectMocks
    private EventUpsertService eventUpsertService;

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 1);

    private SeoulCultureInfoApiResponse.Row buildRow(String title, String place, String start, String end,
                                                      String lat, String lon) {
        return new SeoulCultureInfoApiResponse.Row(
                title, start, end, "전시/미술", place,
                "무료", "02-1234-5678", "설명", "https://link", "https://img",
                lat, lon
        );
    }

    @Nested
    @DisplayName("신규 행사 삽입")
    class Insert {

        @Test
        @DisplayName("existingEventMap에 없는 행사 - 신규 저장")
        void newEvent_savedToRepository() {
            SeoulCultureInfoApiResponse.Row row = buildRow("새 행사", "광화문", "2025-07-01", "2025-07-31", "37.5759", "126.9769");
            Map<String, Event> existingMap = new HashMap<>();

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, existingMap);

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            then(eventJpaRepository).should().save(captor.capture());
            Event saved = captor.getValue();
            assertThat(saved.getEventTitle()).isEqualTo("새 행사");
            assertThat(saved.getLatitude()).isEqualTo(37.5759);
        }

        @Test
        @DisplayName("신규 행사 저장 후 existingEventMap에 추가")
        void newEvent_addedToExistingMap() {
            SeoulCultureInfoApiResponse.Row row = buildRow("새 행사", "광화문", "2025-07-01", "2025-07-31", "37.5759", "126.9769");
            Map<String, Event> existingMap = new HashMap<>();

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, existingMap);

            assertThat(existingMap).containsKey("새 행사|광화문|2025-07-01");
        }
    }

    @Nested
    @DisplayName("기존 행사 업데이트")
    class Update {

        @Test
        @DisplayName("existingEventMap에 있는 행사 - updateDetails 호출 후 저장")
        void existingEvent_updatedAndSaved() {
            Event existing = Event.builder()
                    .eventTitle("기존 행사").place("홍대").startDate(LocalDate.of(2025, 6, 1))
                    .endDate(LocalDate.of(2025, 6, 30)).latitude(37.0).longitude(126.0)
                    .build();
            Map<String, Event> existingMap = new HashMap<>();
            existingMap.put("기존 행사|홍대|2025-06-01", existing);

            SeoulCultureInfoApiResponse.Row row = buildRow("기존 행사", "홍대", "2025-06-01", "2025-08-31", "37.5759", "126.9769");

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, existingMap);

            then(eventJpaRepository).should().save(existing);
            assertThat(existing.getEndDate()).isEqualTo(LocalDate.of(2025, 8, 31));
        }

        @Test
        @DisplayName("좌표가 null인 업데이트 - 기존 좌표 유지")
        void existingEvent_nullCoordinate_keepsExistingCoordinate() {
            Event existing = Event.builder()
                    .eventTitle("기존 행사").place("홍대").startDate(LocalDate.of(2025, 6, 1))
                    .endDate(LocalDate.of(2025, 6, 30)).latitude(37.0).longitude(126.0)
                    .build();
            Map<String, Event> existingMap = new HashMap<>();
            existingMap.put("기존 행사|홍대|2025-06-01", existing);

            SeoulCultureInfoApiResponse.Row row = buildRow("기존 행사", "홍대", "2025-06-01", "2025-08-31", null, null);

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, existingMap);

            assertThat(existing.getLatitude()).isEqualTo(37.0);
            assertThat(existing.getLongitude()).isEqualTo(126.0);
        }
    }

    @Nested
    @DisplayName("필터링 조건")
    class Filtering {

        @Test
        @DisplayName("endDate가 today 이전인 행사 - 저장 안 함")
        void expiredEvent_skipped() {
            SeoulCultureInfoApiResponse.Row row = buildRow("만료 행사", "종로", "2025-01-01", "2025-05-31", "37.5", "126.9");

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, new HashMap<>());

            then(eventJpaRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("startDate가 null인 행사 - 저장 안 함")
        void nullStartDate_skipped() {
            SeoulCultureInfoApiResponse.Row row = buildRow("행사", "종로", null, "2025-12-31", "37.5", "126.9");

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, new HashMap<>());

            then(eventJpaRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("endDate가 null인 행사 - 저장 안 함")
        void nullEndDate_skipped() {
            SeoulCultureInfoApiResponse.Row row = buildRow("행사", "종로", "2025-07-01", null, "37.5", "126.9");

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, new HashMap<>());

            then(eventJpaRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("날짜 형식이 10자 미만인 경우 - 저장 안 함")
        void shortDateString_skipped() {
            SeoulCultureInfoApiResponse.Row row = buildRow("행사", "종로", "2025-07", "2025-12-31", "37.5", "126.9");

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, new HashMap<>());

            then(eventJpaRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("유효 행사와 만료 행사 혼재 - 유효한 것만 저장")
        void mixedRows_onlyValidSaved() {
            SeoulCultureInfoApiResponse.Row valid = buildRow("유효 행사", "마포", "2025-06-01", "2025-12-31", "37.5", "126.9");
            SeoulCultureInfoApiResponse.Row expired = buildRow("만료 행사", "종로", "2024-01-01", "2024-12-31", "37.5", "126.9");

            eventUpsertService.upsertEventBatch(List.of(valid, expired), TODAY, new HashMap<>());

            then(eventJpaRepository).should(times(1)).save(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("좌표 파싱")
    class CoordinateParsing {

        @Test
        @DisplayName("틸다(~) 포함 좌표 - 앞 숫자만 파싱")
        void coordinateWithTilde_parsedCorrectly() {
            SeoulCultureInfoApiResponse.Row row = buildRow("행사", "장소", "2025-07-01", "2025-12-31",
                    "37.5759~38.0", "126.9769~127.0");
            Map<String, Event> existingMap = new HashMap<>();

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, existingMap);

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            then(eventJpaRepository).should().save(captor.capture());
            assertThat(captor.getValue().getLatitude()).isEqualTo(37.5759);
        }

        @Test
        @DisplayName("빈 문자열 좌표 - null로 저장")
        void blankCoordinate_storedAsNull() {
            SeoulCultureInfoApiResponse.Row row = buildRow("행사", "장소", "2025-07-01", "2025-12-31", "", "");
            Map<String, Event> existingMap = new HashMap<>();

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, existingMap);

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            then(eventJpaRepository).should().save(captor.capture());
            assertThat(captor.getValue().getLatitude()).isNull();
            assertThat(captor.getValue().getLongitude()).isNull();
        }

        @Test
        @DisplayName("숫자가 아닌 좌표 문자열 - null로 저장")
        void invalidCoordinateString_storedAsNull() {
            SeoulCultureInfoApiResponse.Row row = buildRow("행사", "장소", "2025-07-01", "2025-12-31", "abc", "xyz");
            Map<String, Event> existingMap = new HashMap<>();

            eventUpsertService.upsertEventBatch(List.of(row), TODAY, existingMap);

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            then(eventJpaRepository).should().save(captor.capture());
            assertThat(captor.getValue().getLatitude()).isNull();
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("한 건 처리 중 예외 발생 - 나머지 건 정상 처리")
        void oneRowFails_restProcessedNormally() {
            SeoulCultureInfoApiResponse.Row badRow = buildRow("행사", "장소", "not-a-date", "2025-12-31", null, null);
            SeoulCultureInfoApiResponse.Row goodRow = buildRow("정상 행사", "홍대", "2025-07-01", "2025-12-31", "37.5", "126.9");

            eventUpsertService.upsertEventBatch(List.of(badRow, goodRow), TODAY, new HashMap<>());

            then(eventJpaRepository).should(times(1)).save(org.mockito.ArgumentMatchers.any());
        }
    }
}
