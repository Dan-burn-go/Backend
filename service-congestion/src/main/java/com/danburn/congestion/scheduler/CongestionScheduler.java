package com.danburn.congestion.scheduler;

import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionApiResponse;
import com.danburn.congestion.event.CongestionBusyEvent;
import com.danburn.congestion.event.CongestionEventPublisher;
import com.danburn.congestion.infra.SeoulApiClient;
import com.danburn.congestion.service.CongestionService;
import com.danburn.congestion.service.CongestionStateTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionScheduler {

    private final SeoulApiClient seoulApiClient;
    private final CongestionService congestionService;
    private final CongestionStateTracker stateTracker;
    private final CongestionEventPublisher eventPublisher;

    @Scheduled(
            fixedRateString = "${congestion.scheduler.interval}",
            initialDelayString = "${congestion.scheduler.initial-delay:10000}"
    )
    public void fetchAndSave() {
        log.info("[CongestionScheduler] 혼잡도 데이터 수집 시작");
        try {
            List<CongestionApiResponse> responses = seoulApiClient.fetchAll();
            List<CongestionRedisDto> dtos = new ArrayList<>();

            for (CongestionApiResponse apiResponse : responses) {
                try {
                    List<CongestionRedisDto.ForecastDto> forecasts = apiResponse.forecasts() != null
                            ? apiResponse.forecasts().stream()
                                .map(f -> new CongestionRedisDto.ForecastDto(
                                        f.forecastTime(), f.congestionLevel(),
                                        f.minPeopleCount(), f.maxPeopleCount()))
                                .toList()
                            : Collections.emptyList();

                    CongestionRedisDto dto = new CongestionRedisDto(
                            apiResponse.areaCode(),
                            apiResponse.congestionLevel(),
                            apiResponse.congestionMessage(),
                            apiResponse.minPeopleCount(),
                            apiResponse.maxPeopleCount(),
                            apiResponse.populationTime(),
                            forecasts
                    );
                    dtos.add(dto);
                } catch (Exception e) {
                    log.warn("[CongestionScheduler] 장소 데이터 변환 실패 - areaCode={}, reason={}",
                            apiResponse.areaCode(), e.getMessage());
                }
            }

            if (!dtos.isEmpty()) {
                checkAndPublishBusyEvents(dtos);
                congestionService.saveAllToRedis(dtos);
                congestionService.saveAllToDb(dtos);
            }

            log.info("[CongestionScheduler] 혼잡도 데이터 수집 완료 - {}/{}건", dtos.size(), responses.size());
        } catch (Exception e) {
            log.error("[CongestionScheduler] 혼잡도 데이터 수집 실패 - {}", e.getMessage(), e);
        }
    }

    private void checkAndPublishBusyEvents(List<CongestionRedisDto> dtos) {
        for (CongestionRedisDto dto : dtos) {
            try {
                CongestionLevel current = CongestionLevel.fromDescription(dto.congestionLevel());
                if (stateTracker.shouldRequestAnalysis(dto.areaCode(), current)) {
                    eventPublisher.publishBusyEvent(new CongestionBusyEvent(
                            dto.areaCode(),
                            dto.congestionLevel(),
                            dto.maxPeopleCount(),
                            dto.populationTime()
                    ));
                }
            } catch (Exception e) {
                log.warn("[CongestionScheduler] 이벤트 발행 실패 - areaCode={}, reason={}",
                        dto.areaCode(), e.getMessage());
            }
        }
    }
}
