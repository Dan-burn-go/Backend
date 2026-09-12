package com.danburn.congestion.repository;

import com.danburn.congestion.domain.AiReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {
    /** createdAt 이 cutoff(createdAfter) 이후인 최신 1건만 조회 — 오래된 리포트의 stale 노출 방지. */
    Optional<AiReport> findTopByAreaCodeAndCreatedAtAfterOrderByCreatedAtDesc(String areaCode, Instant createdAfter);

    /** (area_code, population_time) UNIQUE 기준 idempotent insert. 신규=1, 중복=0.
     *  INSERT IGNORE 사용 — ON DUPLICATE KEY UPDATE 는 useAffectedRows=false(드라이버 기본)에서 중복 시 matched=1 을 반환해 중복 판별 불가. */
    @Transactional
    @Modifying
    @Query(value = "INSERT IGNORE INTO ai_report " +
            "(area_name, area_code, congestion_level, analysis_message, population_time, created_at, updated_at) " +
            "VALUES (:areaName, :areaCode, :congestionLevel, :analysisMessage, :populationTime, NOW(), NOW())",
            nativeQuery = true)
    int insertIfAbsent(@Param("areaName") String areaName,
                       @Param("areaCode") String areaCode,
                       @Param("congestionLevel") String congestionLevel,
                       @Param("analysisMessage") String analysisMessage,
                       @Param("populationTime") String populationTime);
}
