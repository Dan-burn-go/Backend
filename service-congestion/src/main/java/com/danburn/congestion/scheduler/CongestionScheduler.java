package com.danburn.congestion.scheduler;

import com.danburn.congestion.domain.CongestionLevel;
import com.danburn.congestion.domain.PopulationTrend;
import com.danburn.congestion.dto.CongestionRedisDto;
import com.danburn.congestion.dto.response.CongestionApiResponse;
import com.danburn.congestion.infra.SeoulApiClient;
import com.danburn.congestion.service.CongestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionScheduler {

    private final SeoulApiClient seoulApiClient;
    private final CongestionService congestionService;

    @Scheduled(
            fixedRateString = "${congestion.scheduler.interval}",
            initialDelayString = "${congestion.scheduler.initial-delay:10000}"
    )
    public void fetchAndSave() {
        log.info("[CongestionScheduler] 혼잡도 데이터 수집 시작");
        try {
            List<CongestionApiResponse> responses = seoulApiClient.fetchAll();
            List<CongestionRedisDto> dtos = new ArrayList<>();

            for (int i = 0; i < responses.size(); i++) {
                CongestionApiResponse apiResponse = responses.get(i);
                try {
                    // TODO: locationId를 areaName 해시로 임시 할당. 실제 API 연동 시 장소 코드 기반으로 변경 필요.
                    CongestionRedisDto dto = new CongestionRedisDto(
                            (long) Math.abs(apiResponse.areaName().hashCode()),
                            apiResponse.areaName(),
                            CongestionLevel.fromDescription(apiResponse.congestionLevel()),
                            apiResponse.minPeopleCount(),
                            apiResponse.maxPeopleCount(),
                            PopulationTrend.fromDescription(apiResponse.populationTrend()),
                            Instant.now()
                    );
                    dtos.add(dto);
                } catch (Exception e) {
                    log.warn("[CongestionScheduler] 장소 데이터 변환 실패 - areaName={}, reason={}",
                            apiResponse.areaName(), e.getMessage());
                }
            }

            if (!dtos.isEmpty()) {
                congestionService.saveAllToRedis(dtos);
                congestionService.saveAllToDb(dtos);
            }

            log.info("[CongestionScheduler] 혼잡도 데이터 수집 완료 - {}/{}건", dtos.size(), responses.size());
        } catch (Exception e) {
            log.error("[CongestionScheduler] 혼잡도 데이터 수집 실패 - {}", e.getMessage(), e);
        }
    }
}
