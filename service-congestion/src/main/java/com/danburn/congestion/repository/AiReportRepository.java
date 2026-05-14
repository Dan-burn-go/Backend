package com.danburn.congestion.repository;

import com.danburn.congestion.domain.AiReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {
    Optional<AiReport> findTopByAreaCodeOrderByCreatedAtDesc(String areaCode);

    /** (area_code, population_time) UNIQUE 기준 atomic idempotent insert. 신규=1, 중복=0. */
    @Modifying
    @Query(value = "INSERT INTO ai_report " +
            "(area_name, area_code, congestion_level, analysis_message, population_time, created_at, updated_at) " +
            "VALUES (:areaName, :areaCode, :congestionLevel, :analysisMessage, :populationTime, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE area_code = area_code",
            nativeQuery = true)
    int insertIfAbsent(@Param("areaName") String areaName,
                       @Param("areaCode") String areaCode,
                       @Param("congestionLevel") String congestionLevel,
                       @Param("analysisMessage") String analysisMessage,
                       @Param("populationTime") String populationTime);
}
