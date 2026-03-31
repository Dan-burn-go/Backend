package com.danburn.congestion.scheduler;

import com.danburn.congestion.service.CongestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DataCleanupSchedulerTest {

    @Mock
    private CongestionService congestionService;

    @InjectMocks
    private DataCleanupScheduler dataCleanupScheduler;

    @Test
    @DisplayName("cleanupOldData: retentionDays 기준으로 deleteOlderThan 호출")
    void cleanupOldData_success() {
        ReflectionTestUtils.setField(dataCleanupScheduler, "retentionDays", 7);
        given(congestionService.deleteOlderThan(any(Instant.class))).willReturn(10);

        dataCleanupScheduler.cleanupOldData();

        then(congestionService).should().deleteOlderThan(any(Instant.class));
    }

    @Test
    @DisplayName("cleanupOldData: deleteOlderThan 예외 발생 → 예외 전파 없음")
    void cleanupOldData_exception() {
        ReflectionTestUtils.setField(dataCleanupScheduler, "retentionDays", 7);
        given(congestionService.deleteOlderThan(any(Instant.class)))
                .willThrow(new RuntimeException("DB 연결 실패"));

        dataCleanupScheduler.cleanupOldData();

        then(congestionService).should().deleteOlderThan(any(Instant.class));
    }
}
