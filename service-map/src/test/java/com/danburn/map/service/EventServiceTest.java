package com.danburn.map.service;

import com.danburn.map.domain.Event;
import com.danburn.map.dto.response.SeoulCultureInfoApiResponse;
import com.danburn.map.infra.SeoulCultureInfoApiClient;
import com.danburn.map.repository.EventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private SeoulCultureInfoApiClient apiClient;

    @Mock
    private EventUpsertService eventUpsertService;

    @Mock
    private EventJpaRepository eventJpaRepository;

    @InjectMocks
    private EventService eventService;

    private SeoulCultureInfoApiResponse buildResponse(int totalCount, List<SeoulCultureInfoApiResponse.Row> rows) {
        return new SeoulCultureInfoApiResponse(
                new SeoulCultureInfoApiResponse.CulturalEventInfo(totalCount, rows)
        );
    }

    private SeoulCultureInfoApiResponse.Row buildRow(String title) {
        return new SeoulCultureInfoApiResponse.Row(
                title, "2025-07-01", "2025-12-31", "전시", "광화문",
                "무료", "02-1234-5678", "설명", "https://link", "https://img",
                "37.5759", "126.9769"
        );
    }

    @Nested
    @DisplayName("fetchAndSyncEvents - 정상 흐름")
    class NormalFlow {

        @Test
        @DisplayName("단일 배치(1000건 미만) 응답 - upsert 1회 호출 후 deleteByEndDateBefore")
        void singleBatch_upsertOnce_thenDelete() {
            given(eventJpaRepository.findAll()).willReturn(List.of());
            given(apiClient.fetchEvents(any()))
                    .willReturn(buildResponse(500, List.of(buildRow("행사1"))));

            eventService.fetchAndSyncEvents();

            then(eventUpsertService).should(times(1)).upsertEventBatch(anyList(), any(), anyMap());
            then(eventJpaRepository).should().deleteByEndDateBefore(any(LocalDate.class));
        }

        @Test
        @DisplayName("null 응답 - upsert 호출 안 함, deleteByEndDateBefore는 호출")
        void nullResponse_noUpsert_deletesCalled() {
            given(eventJpaRepository.findAll()).willReturn(List.of());
            given(apiClient.fetchEvents(any())).willReturn(null);

            eventService.fetchAndSyncEvents();

            then(eventUpsertService).should(never()).upsertEventBatch(anyList(), any(), anyMap());
            then(eventJpaRepository).should().deleteByEndDateBefore(any(LocalDate.class));
        }

        @Test
        @DisplayName("응답 row가 null인 경우 - upsert 호출 안 함")
        void nullRows_noUpsert() {
            given(eventJpaRepository.findAll()).willReturn(List.of());
            given(apiClient.fetchEvents(any()))
                    .willReturn(new SeoulCultureInfoApiResponse(
                            new SeoulCultureInfoApiResponse.CulturalEventInfo(0, null)
                    ));

            eventService.fetchAndSyncEvents();

            then(eventUpsertService).should(never()).upsertEventBatch(anyList(), any(), anyMap());
        }

        @Test
        @DisplayName("기존 이벤트가 있을 때 - existingEventMap 초기화 후 upsert 전달")
        void existingEvents_buildMapFromDb() {
            Event existing = Event.builder()
                    .eventTitle("기존 행사").place("홍대").startDate(LocalDate.of(2025, 1, 1))
                    .endDate(LocalDate.of(2025, 12, 31)).build();
            given(eventJpaRepository.findAll()).willReturn(List.of(existing));
            given(apiClient.fetchEvents(any()))
                    .willReturn(buildResponse(1, List.of(buildRow("신규 행사"))));

            eventService.fetchAndSyncEvents();

            then(eventUpsertService).should().upsertEventBatch(anyList(), any(), anyMap());
        }

        @Test
        @DisplayName("첫 배치 응답에서 listTotalCount로 maxBatches 재계산")
        void firstBatchTotalCount_recalculatesMaxBatches() {
            // totalCount=2000 → 2 batches
            List<SeoulCultureInfoApiResponse.Row> fullBatch = List.of(buildRow("행사1"));
            // First response: 2000 total, 1000 rows (full)
            SeoulCultureInfoApiResponse firstResponse = new SeoulCultureInfoApiResponse(
                    new SeoulCultureInfoApiResponse.CulturalEventInfo(2000,
                            java.util.Collections.nCopies(1000, buildRow("행사")))
            );
            SeoulCultureInfoApiResponse secondResponse = buildResponse(2000, fullBatch);

            given(eventJpaRepository.findAll()).willReturn(List.of());
            given(apiClient.fetchEvents(any()))
                    .willReturn(firstResponse)
                    .willReturn(secondResponse);

            eventService.fetchAndSyncEvents();

            then(eventUpsertService).should(times(2)).upsertEventBatch(anyList(), any(), anyMap());
        }
    }

    @Nested
    @DisplayName("fetchAndSyncEvents - 예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("API 호출 예외 - 예외 로그 후 다음 배치 진행, deleteByEndDateBefore 호출")
        void apiException_logsAndContinues_deletesCalled() {
            given(eventJpaRepository.findAll()).willReturn(List.of());
            given(apiClient.fetchEvents(any())).willThrow(new RuntimeException("API 오류"));

            eventService.fetchAndSyncEvents();

            then(eventUpsertService).should(never()).upsertEventBatch(anyList(), any(), anyMap());
            then(eventJpaRepository).should().deleteByEndDateBefore(any(LocalDate.class));
        }
    }
}
