package com.danburn.congestion.infra;

import com.danburn.congestion.dto.response.CongestionApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@ConditionalOnProperty(name = "seoul.api.key", matchIfMissing = false)
@Component
public class RealSeoulApiClient implements SeoulApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final ExecutorService executor;

    private static final String URL_TEMPLATE =
            "http://openapi.seoul.go.kr:8088/{apiKey}/json/citydata_ppltn/1/5/{areaName}";
    public RealSeoulApiClient(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${seoul.api.key}") String apiKey,
            @Value("${seoul.api.thread-pool-size:10}") int threadPoolSize) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("seoul.api.key must be configured");
        }
        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory();
        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        this.restTemplate = restTemplateBuilder
                .uriTemplateHandler(uriFactory)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<CongestionApiResponse> fetchAll() {
        List<CompletableFuture<CongestionApiResponse>> futures = SeoulArea.all().stream()
                .map(area -> CompletableFuture.supplyAsync(() -> fetchOne(area), executor))
                .toList();

        return futures.stream()
                .map(future -> {
                    try {
                        return future.join();
                    } catch (Exception e) {
                        log.error("[SeoulApiClient] 비동기 작업 실패: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private CongestionApiResponse fetchOne(SeoulArea area) {
        try {
            String response = restTemplate.getForObject(
                    URL_TEMPLATE, String.class, apiKey, area.getName());
            return parseResponse(response, area);
        } catch (Exception e) {
            log.warn("[SeoulApiClient] API 호출 실패 - area={}, error={}",
                    area.getName(), e.getClass().getSimpleName());
            return null;
        }
    }

    private CongestionApiResponse parseResponse(String json, SeoulArea area) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode resultNode = root.path("RESULT");
            String resultCode = resultNode.path("RESULT.CODE").asText();
            if (!"INFO-000".equals(resultCode)) {
                log.warn("[SeoulApiClient] API 오류 응답 - area={}, code={}, message={}",
                        area.getName(), resultCode, resultNode.path("RESULT.MESSAGE").asText());
                return null;
            }

            JsonNode data = root.path("SeoulRtd.citydata_ppltn").get(0);
            if (data == null) {
                log.warn("[SeoulApiClient] 응답 데이터 없음 - area={}", area.getName());
                return null;
            }

            List<CongestionApiResponse.ForecastResponse> forecasts = new ArrayList<>();
            JsonNode fcstNode = data.path("FCST_PPLTN");
            if (fcstNode.isArray()) {
                for (JsonNode fcst : fcstNode) {
                    forecasts.add(new CongestionApiResponse.ForecastResponse(
                            fcst.path("FCST_TIME").asText(),
                            fcst.path("FCST_CONGEST_LVL").asText(),
                            fcst.path("FCST_PPLTN_MIN").asInt(),
                            fcst.path("FCST_PPLTN_MAX").asInt()
                    ));
                }
            }

            return new CongestionApiResponse(
                    data.path("AREA_NM").asText(),
                    data.path("AREA_CD").asText(),
                    data.path("AREA_CONGEST_LVL").asText(),
                    data.path("AREA_CONGEST_MSG").asText(),
                    data.path("AREA_PPLTN_MIN").asInt(),
                    data.path("AREA_PPLTN_MAX").asInt(),
                    data.path("PPLTN_TIME").asText(),
                    forecasts
            );
        } catch (Exception e) {
            log.warn("[SeoulApiClient] 응답 파싱 실패 - area={}, error={}",
                    area.getName(), e.getClass().getSimpleName());
            return null;
        }
    }
}
