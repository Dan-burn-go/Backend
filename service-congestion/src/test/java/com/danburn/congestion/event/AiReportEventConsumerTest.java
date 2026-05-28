package com.danburn.congestion.event;

import com.danburn.congestion.repository.AiReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AiReportEventConsumerTest {

    @Mock
    private AiReportRepository aiReportRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiReportEventConsumer consumer;

    private AiReportEvent event() {
        return new AiReportEvent(
                "명동", "POI001", "붐빔", "분석 결과 메시지", "2026-04-01 14:00"
        );
    }

    @Nested
    @DisplayName("handleAiReport")
    class HandleAiReport {

        @Test
        @DisplayName("신규 삽입(inserted=1) → Redis 캐싱")
        void insertedAndCached() {
            given(aiReportRepository.insertIfAbsent(any(), any(), any(), any(), any())).willReturn(1);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);

            consumer.handleAiReport(event());

            then(aiReportRepository).should().insertIfAbsent(
                    eq("명동"), eq("POI001"), eq("붐빔"), eq("분석 결과 메시지"), eq("2026-04-01 14:00")
            );
            then(valueOps).should().set(eq("ai-report:POI001"), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("중복 메시지(inserted=0) → Redis 캐싱 안 함")
        void duplicateIgnored() {
            given(aiReportRepository.insertIfAbsent(any(), any(), any(), any(), any())).willReturn(0);

            consumer.handleAiReport(event());

            then(stringRedisTemplate).shouldHaveNoInteractions();
        }
    }
}
