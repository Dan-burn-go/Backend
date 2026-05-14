package com.danburn.congestion.event;

import com.danburn.congestion.config.rabbitmq.RabbitMqConfig;
import com.danburn.congestion.repository.AiReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReportEventConsumer {

    private static final String REDIS_KEY_PREFIX = "ai-report:";
    private static final Duration REDIS_TTL = Duration.ofHours(4);

    private final AiReportRepository aiReportRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.AI_REPORT_QUEUE_NAME)
    public void handleAiReport(AiReportEvent event) {
        int inserted = aiReportRepository.insertIfAbsent(
                event.areaName(),
                event.areaCode(),
                event.congestionLevel(),
                event.analysisMessage(),
                event.populationTime());

        if (inserted == 0) {
            log.warn("[AiReportConsumer] 중복 메시지 무시 - areaCode={}, populationTime={}",
                    event.areaCode(), event.populationTime());
            return;
        }

        cacheToRedis(event);
        log.info("[AiReportConsumer] AI 분석 결과 저장 완료 - areaCode={}", event.areaCode());
    }

    private void cacheToRedis(AiReportEvent event) {
        try {
            String key = REDIS_KEY_PREFIX + event.areaCode();
            String value = objectMapper.writeValueAsString(event);
            stringRedisTemplate.opsForValue().set(key, value, REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.error("[AiReportConsumer] Redis 캐싱 실패 - areaCode={}, error={}", event.areaCode(), e.getMessage());
        }
    }
}
