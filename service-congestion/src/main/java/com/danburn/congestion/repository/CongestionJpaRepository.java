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

    Optional<Congestion> findTopByLocationIdOrderByCreatedAtDesc(Long locationId);

    List<Congestion> findByLocationId(Long locationId);

    @Query("SELECT c FROM Congestion c WHERE c.createdAt = " +
            "(SELECT MAX(c2.createdAt) FROM Congestion c2 WHERE c2.locationId = c.locationId)")
    List<Congestion> findLatestPerLocation();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Congestion c WHERE c.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
