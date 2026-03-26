package com.danburn.domain.congestion.repository;

import com.danburn.domain.congestion.domain.Congestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CongestionJpaRepository extends JpaRepository<Congestion, Long> {

    Optional<Congestion> findTopByLocationIdOrderByCreatedAtDesc(Long locationId);

    List<Congestion> findByLocationId(Long locationId);
}
