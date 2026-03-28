package com.danburn.congestion.infra;

import com.danburn.congestion.dto.response.CongestionApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * API 키 발급 전까지 사용하는 더미 구현체.
 * 실제 SeoulApiClient 구현 시 이 클래스를 삭제한다.
 */
@Slf4j
@Profile({"local", "dev"})
@Component
public class StubSeoulApiClient implements SeoulApiClient {

    @Override
    public List<CongestionApiResponse> fetchAll() {
        log.warn("[STUB] 더미 데이터를 반환합니다. 실제 공공 API 클라이언트로 교체 필요.");

        return List.of(
                new CongestionApiResponse(
                        "명동",
                        "POI001",
                        "붐빔",
                        "사람들이 몰려있을 가능성이 매우 크고 많이 붐빈다고 느낄 수 있어요.",
                        32000, 34000,
                        "2026-03-28 14:05",
                        List.of(
                                new CongestionApiResponse.ForecastResponse("2026-03-28 15:00", "약간 붐빔", 28000, 30000),
                                new CongestionApiResponse.ForecastResponse("2026-03-28 16:00", "보통", 24000, 26000)
                        )
                ),
                new CongestionApiResponse(
                        "홍대입구역",
                        "POI002",
                        "약간 붐빔",
                        "사람이 다소 많은 편이에요.",
                        28000, 30000,
                        "2026-03-28 14:05",
                        List.of(
                                new CongestionApiResponse.ForecastResponse("2026-03-28 15:00", "보통", 22000, 24000),
                                new CongestionApiResponse.ForecastResponse("2026-03-28 16:00", "여유", 18000, 20000)
                        )
                ),
                new CongestionApiResponse(
                        "강남역",
                        "POI003",
                        "보통",
                        "사람이 적당히 있는 편이에요.",
                        18000, 20000,
                        "2026-03-28 14:05",
                        List.of(
                                new CongestionApiResponse.ForecastResponse("2026-03-28 15:00", "여유", 14000, 16000),
                                new CongestionApiResponse.ForecastResponse("2026-03-28 16:00", "여유", 10000, 12000)
                        )
                )
        );
    }
}
