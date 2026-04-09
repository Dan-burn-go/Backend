package com.danburn.congestion.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.congestion.domain.AiReport;
import com.danburn.congestion.dto.response.AireportApiResponse;
import com.danburn.congestion.repository.AiReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiReportRepository aiReportRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiService aiService;

    private AiReport createAiReport(String areaCode, String areaName) {
        return AiReport.builder()
                .areaCode(areaCode)
                .areaName(areaName)
                .congestionLevel("붐빔")
                .analysisMessage("주말 나들이 인파로 인한 혼잡")
                .populationTime("2026-04-01 14:00")
                .build();
    }

    @Test
    @DisplayName("Redis 캐시 히트 → Redis 데이터 반환")
    void getLatestAiReport_redisHit() {
        String json = """
                {"areaCode":"POI001","areaName":"광화문·덕수궁","congestionLevel":"붐빔","analysisMessage":"주말 나들이 인파로 인한 혼잡","populationTime":"2026-04-01 14:00"}""";
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("ai-report:POI001")).willReturn(json);

        AireportApiResponse result = aiService.getLatestAiReport("POI001");

        assertThat(result.areaCode()).isEqualTo("POI001");
        assertThat(result.areaName()).isEqualTo("광화문·덕수궁");
        assertThat(result.analysisMessage()).isEqualTo("주말 나들이 인파로 인한 혼잡");
        then(aiReportRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Redis 미스 → DB 폴백 조회")
    void getLatestAiReport_redisMiss() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("ai-report:POI001")).willReturn(null);
        given(aiReportRepository.findTopByAreaCodeOrderByCreatedAtDesc("POI001"))
                .willReturn(Optional.of(createAiReport("POI001", "광화문·덕수궁")));

        AireportApiResponse result = aiService.getLatestAiReport("POI001");

        assertThat(result.areaCode()).isEqualTo("POI001");
        assertThat(result.areaName()).isEqualTo("광화문·덕수궁");
    }

    @Test
    @DisplayName("Redis 역직렬화 실패 → DB 폴백 조회")
    void getLatestAiReport_redisParseError() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("ai-report:POI001")).willReturn("{잘못된 JSON}");
        given(aiReportRepository.findTopByAreaCodeOrderByCreatedAtDesc("POI001"))
                .willReturn(Optional.of(createAiReport("POI001", "광화문·덕수궁")));

        AireportApiResponse result = aiService.getLatestAiReport("POI001");

        assertThat(result.areaCode()).isEqualTo("POI001");
    }

    @Test
    @DisplayName("Redis 미스 + DB 미스 → GlobalException(404)")
    void getLatestAiReport_notFound() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("ai-report:POI999")).willReturn(null);
        given(aiReportRepository.findTopByAreaCodeOrderByCreatedAtDesc("POI999"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> aiService.getLatestAiReport("POI999"))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getStatus()).isEqualTo(404));
    }
}
