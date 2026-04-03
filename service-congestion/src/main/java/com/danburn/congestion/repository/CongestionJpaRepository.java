package com.danburn.congestion.repository;

import com.danburn.congestion.domain.Congestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
