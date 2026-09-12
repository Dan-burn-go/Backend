package com.danburn.map.infra;

import com.danburn.map.dto.response.CongestionApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CongestionApiClient {

    private final WebClient webClient;
    private final CongestionCacheRepository congestionCacheRepository;

    public CongestionApiClient(@Value("${congestion.service.url}") String congestionServiceUrl,
                               CongestionCacheRepository congestionCacheRepository) {
        this.webClient = WebClient.builder()
                .baseUrl(congestionServiceUrl)
                .build();
        this.congestionCacheRepository = congestionCacheRepository;
    }

    @CircuitBreaker(name = "congestion", fallbackMethod = "getCongestionLevelFallback")
    @TimeLimiter(name = "congestion")
    public Mono<String> getCongestionLevel(String areaCode) {
        return webClient.get()
                .uri("/api/congestion/{areaCode}", areaCode)
                .retrieve()
                .bodyToMono(CongestionApiResponse.class)
                .mapNotNull(response ->
                        response.data() != null ? response.data().congestionLevel() : null)
                // 성공 응답을 fallback 대비 캐시에 보존
                .doOnNext(level -> congestionCacheRepository.save(areaCode, level));
    }

    // 서킷 Open·타임아웃·장애 시 캐시된 최근 혼잡도로 폴백, 없으면 empty(혼잡도 null)로 부분 실패 허용
    private Mono<String> getCongestionLevelFallback(String areaCode, Throwable t) {
        log.warn("혼잡도 서킷 브레이커 작동 - 캐시 폴백, areaCode: {}, error: {}", areaCode, t.getMessage());
        return congestionCacheRepository.find(areaCode)
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }
}
