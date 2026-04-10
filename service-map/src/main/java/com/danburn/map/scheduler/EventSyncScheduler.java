package com.danburn.map.scheduler;

import com.danburn.map.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSyncScheduler {

    private final EventService eventService;

    // 매주 일요일 새벽 3시 0분 0초에 실행 (초 분 시 일 월 요일)
    @Scheduled(cron = "0 0 3 * * SUN")
    public void syncCulturalEvents() {
        log.info("[Scheduler] 주간 서울시 문화행사 동기화  시작");
        eventService.fetchAndSyncEvents();
        log.info("[Scheduler] 주간 서울시 문화행사 동기화  종료");
    }
}