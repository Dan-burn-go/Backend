package com.danburn.congestion.repository;

import com.danburn.congestion.domain.Congestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CongestionJpaRepository extends JpaRepository<Congestion, Long> {

    Optional<Congestion> findTopByAreaCodeOrderByCreatedAtDesc(String areaCode);

    List<Congestion> findByAreaCode(String areaCode);

    @Query("SELECT c FROM Congestion c WHERE c.createdAt = " +
            "(SELECT MAX(c2.createdAt) FROM Congestion c2 WHERE c2.areaCode = c.areaCode)")
    List<Congestion> findLatestPerLocation();

    @Query("SELECT c FROM Congestion c WHERE c.areaCode IN :areaCodes AND c.createdAt = " +
            "(SELECT MAX(c2.createdAt) FROM Congestion c2 WHERE c2.areaCode = c.areaCode)")
    List<Congestion> findLatestByAreaCodes(@Param("areaCodes") List<String> areaCodes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Congestion c WHERE c.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

    /**
     * 시간별 혼잡도 추이: 특정 장소의 시간대별 평균 데이터
     * Returns: [hour, congestionLevel, avgMin, avgMax, count]
     */
    @Query(value = """
            SELECT HOUR(c.population_time) AS hr,
                   c.congestion_level,
                   AVG(c.min_people_count),
                   AVG(c.max_people_count),
                   COUNT(*)
            FROM congestion c
            WHERE c.area_code = :areaCode
              AND c.population_time >= :since
            GROUP BY hr, c.congestion_level
            ORDER BY hr
            """, nativeQuery = true)
    List<Object[]> findHourlyTrend(@Param("areaCode") String areaCode,
                                   @Param("since") LocalDateTime since);

    /**
     * 요일별 혼잡도 추이: 특정 장소의 요일별 평균 데이터
     * DAYOFWEEK: 1=일, 2=월, ..., 7=토
     * Returns: [dayOfWeek, congestionLevel, avgMin, avgMax, count]
     */
    @Query(value = """
            SELECT DAYOFWEEK(c.population_time) AS dow,
                   c.congestion_level,
                   AVG(c.min_people_count),
                   AVG(c.max_people_count),
                   COUNT(*)
            FROM congestion c
            WHERE c.area_code = :areaCode
              AND c.population_time >= :since
            GROUP BY dow, c.congestion_level
            ORDER BY dow
            """, nativeQuery = true)
    List<Object[]> findDailyTrend(@Param("areaCode") String areaCode,
                                  @Param("since") LocalDateTime since);
}
