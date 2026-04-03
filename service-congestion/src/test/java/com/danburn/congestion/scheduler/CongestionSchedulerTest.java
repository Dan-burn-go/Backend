package com.danburn.congestion.scheduler;

import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionApiResponse;
import com.danburn.congestion.event.CongestionEventPublisher;
import com.danburn.congestion.infra.SeoulApiClient;
import com.danburn.congestion.service.CongestionService;
import com.danburn.congestion.service.CongestionStateTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CongestionSchedulerTest {

    @Mock
    private SeoulApiClient seoulApiClient;

    @Mock
    private CongestionService congestionService;

    @Mock
    private CongestionStateTracker stateTracker;

    @Mock
    private CongestionEventPublisher eventPublisher;

    @InjectMocks
    private CongestionScheduler congestionScheduler;

    private CongestionApiResponse createApiResponse(String areaCode, String level) {
        return new CongestionApiResponse(
                "테스트장소", areaCode, level,
                "혼잡도 메시지", 10000, 12000,
                "2026-04-01 14:00",
                List.of(new CongestionApiResponse.ForecastResponse(
                        "2026-04-01 15:00", "보통", 8000, 10000
                ))
        );
    }

    @Test
    @DisplayName("API 응답 정상 → Redis + DB 저장 호출")
    void fetchAndSave_success() {
        given(seoulApiClient.fetchAll()).willReturn(List.of(
                createApiResponse("POI001", "붐빔"),
                createApiResponse("POI002", "여유")
        ));
        given(stateTracker.filterAreaCodesForAnalysis(anyList())).willReturn(Collections.emptyList());

        congestionScheduler.fetchAndSave();

        then(congestionService).should().saveAllToRedis(anyList());
        then(congestionService).should().saveAllToDb(anyList());
    }

    @Test
    @DisplayName("API 응답이 빈 리스트 → 저장 호출 안 함")
    void fetchAndSave_emptyResponse() {
        given(seoulApiClient.fetchAll()).willReturn(Collections.emptyList());

        congestionScheduler.fetchAndSave();

        then(congestionService).should(never()).saveAllToRedis(anyList());
        then(congestionService).should(never()).saveAllToDb(anyList());
    }

    @Test
    @DisplayName("API 호출 예외 발생 → 저장 호출 안 함 (예외 전파 없음)")
    void fetchAndSave_apiException() {
        given(seoulApiClient.fetchAll()).willThrow(new RuntimeException("API 서버 장애"));

        congestionScheduler.fetchAndSave();

        then(congestionService).should(never()).saveAllToRedis(anyList());
        then(congestionService).should(never()).saveAllToDb(anyList());
    }

    @Test
    @DisplayName("forecast가 null인 응답 → 빈 리스트로 변환되어 저장")
    void fetchAndSave_nullForecasts() {
        CongestionApiResponse responseWithNullForecasts = new CongestionApiResponse(
                "테스트장소", "POI001", "보통",
                "메시지", 5000, 6000,
                "2026-04-01 14:00", null
        );
        given(seoulApiClient.fetchAll()).willReturn(List.of(responseWithNullForecasts));
        given(stateTracker.filterAreaCodesForAnalysis(anyList())).willReturn(Collections.emptyList());

        congestionScheduler.fetchAndSave();

        then(congestionService).should().saveAllToRedis(anyList());
    }
}
