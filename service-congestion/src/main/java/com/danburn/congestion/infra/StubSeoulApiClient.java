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
                new CongestionApiResponse("명동", "붐빔", 32000, 34000, "증가"),
                new CongestionApiResponse("홍대입구역", "약간 붐빔", 28000, 30000, "유지"),
                new CongestionApiResponse("강남역", "보통", 18000, 20000, "감소")
        );
    }
}
