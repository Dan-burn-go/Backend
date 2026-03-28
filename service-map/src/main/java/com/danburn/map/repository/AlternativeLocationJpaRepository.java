package com.danburn.map.repository;

import com.danburn.map.domain.AlternativeLocation;
import com.danburn.map.domain.AlternativeLocationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlternativeLocationJpaRepository extends JpaRepository<AlternativeLocation, AlternativeLocationId> {
  
  @Query("SELECT a FROM AlternativeLocation a WHERE a.locationId = :locationId ORDER BY a.priority ASC")
  List<AlternativeLocation> findByLocationId(@Param("locationId") Long locationId);
  Optional<AlternativeLocation> findByLocationIdAndAlternativeLocationId(Long locationId, Long alternativeLocationId);
}
