package com.danburn.congestion.repository;

import com.danburn.congestion.domain.AiReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {
    Optional<AiReport> findTopByAreaCodeOrderByCreatedAtDesc(String areaCode);
    
}
