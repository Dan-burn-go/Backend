package com.danburn.congestion.infra;

import com.danburn.congestion.dto.response.CongestionApiResponse;

import java.util.List;

/**
 * 서울 실시간 도시데이터 공공 API 호출 인터페이스.
 * 구현체는 API 키 발급 후 작성 (StubSeoulApiClient → 실제 클라이언트로 교체).
 */
public interface SeoulApiClient {

    List<CongestionApiResponse> fetchAll();
}
