package com.danburn.congestion.repository;

import com.danburn.congestion.domain.AiReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {
}
