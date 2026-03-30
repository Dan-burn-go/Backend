package com.danburn.map.repository;

import com.danburn.map.domain.Congestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CongestionJpaRepository extends JpaRepository<Congestion, Long> {
  Congestion findByLocationApiAreaCode(String apiAreaCode);
  Congestion findByLocationLocationName(String locationName);
}
