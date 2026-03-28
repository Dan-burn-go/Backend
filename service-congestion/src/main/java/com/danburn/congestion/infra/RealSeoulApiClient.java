package com.danburn.congestion.infra;

import com.danburn.congestion.dto.response.CongestionApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Profile("!stub")
@Component
public class RealSeoulApiClient implements SeoulApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    private static final String BASE_URL = "http://openapi.seoul.go.kr:8088";
    private static final int THREAD_POOL_SIZE = 10;

    public RealSeoulApiClient(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${seoul.api.key}") String apiKey) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public List<CongestionApiResponse> fetchAll() {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<CompletableFuture<CongestionApiResponse>> futures = SeoulArea.all().stream()
                    .map(area -> CompletableFuture.supplyAsync(() -> fetchOne(area), executor))
                    .toList();

            return futures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } finally {
            executor.shutdown();
        }
    }

    private CongestionApiResponse fetchOne(SeoulArea area) {
        try {
            String encodedName = URLEncoder.encode(area.getName(), StandardCharsets.UTF_8);
            String url = String.format("%s/%s/json/citydata_ppltn/1/5/%s",
                    BASE_URL, apiKey, encodedName);

            String response = restTemplate.getForObject(url, String.class);
            return parseResponse(response, area);
        } catch (Exception e) {
            log.warn("[SeoulApiClient] API 호출 실패 - area={}, reason={}",
                    area.getName(), e.getMessage());
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
            log.warn("[SeoulApiClient] 응답 파싱 실패 - area={}, reason={}",
                    area.getName(), e.getMessage());
            return null;
        }
    }
}
