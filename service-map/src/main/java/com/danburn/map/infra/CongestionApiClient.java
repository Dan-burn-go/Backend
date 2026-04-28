package com.danburn.map.infra;

import com.danburn.map.dto.response.CongestionApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class CongestionApiClient {

    private final WebClient webClient;

    public CongestionApiClient(@Value("${congestion.service.url}") String congestionServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(congestionServiceUrl)
                .build();
    }

    public String getCongestionLevel(String areaCode) {
        try {
            CongestionApiResponse response = webClient.get()
                    .uri("/api/congestion/{areaCode}", areaCode)
                    .retrieve()
                    .bodyToMono(CongestionApiResponse.class)
                    .block();

            if (response != null && response.data() != null) {
                return response.data().congestionLevel();
            }
            return null;
        } catch (Exception e) {
            log.warn("혼잡도 조회 실패 - areaCode: {}, error: {}", areaCode, e.getMessage());
            return null;
        }
    }
}
