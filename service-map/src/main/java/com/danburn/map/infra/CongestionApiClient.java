package com.danburn.map.infra;

import com.danburn.map.dto.response.CongestionApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class CongestionApiClient {

    private final WebClient webClient;

    public CongestionApiClient(@Value("${congestion.service.url}") String congestionServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(congestionServiceUrl)
                .build();
    }

    public Mono<String> getCongestionLevel(String areaCode) {
        return webClient.get()
                .uri("/api/congestion/{areaCode}", areaCode)
                .retrieve()
                .bodyToMono(CongestionApiResponse.class)
                // Netty 3초 타임아웃
                .timeout(Duration.ofSeconds(3))
                .mapNotNull(response ->
                        response.data() != null ? response.data().congestionLevel() : null)
                .onErrorResume(e -> {
                    log.warn("혼잡도 조회 실패 - areaCode: {}, error: {}", areaCode, e.getMessage());
                    return Mono.empty();
                });
    }
}
