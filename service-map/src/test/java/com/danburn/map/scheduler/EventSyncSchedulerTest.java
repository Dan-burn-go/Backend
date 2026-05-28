package com.danburn.map.scheduler;

import com.danburn.map.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class EventSyncSchedulerTest {

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventSyncScheduler eventSyncScheduler;

    @Test
    @DisplayName("syncCulturalEvents - EventService.fetchAndSyncEvents 1회 호출")
    void syncCulturalEvents_callsFetchAndSyncOnce() {
        eventSyncScheduler.syncCulturalEvents();

        then(eventService).should().fetchAndSyncEvents();
    }

    @Test
    @DisplayName("syncCulturalEvents - EventService 예외 발생 시 전파됨")
    void syncCulturalEvents_exceptionPropagated() {
        willThrow(new RuntimeException("동기화 실패"))
                .given(eventService).fetchAndSyncEvents();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> eventSyncScheduler.syncCulturalEvents())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("동기화 실패");
    }
}
