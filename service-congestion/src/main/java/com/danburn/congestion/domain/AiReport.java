package com.danburn.congestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_report_id")
    private Long id;

    @Column(name = "area_code", nullable = false, length = 50)
    private String areaCode;

    @Column(name = "congestion_level", nullable = false, length = 20)
    private String congestionLevel;

    @Column(name = "analysis_message", nullable = false, columnDefinition = "TEXT")
    private String analysisMessage;

    @Column(name = "population_time", nullable = false, length = 30)
    private String populationTime;

    @Builder
    private AiReport(String areaCode, String congestionLevel, String analysisMessage, String populationTime) {
        this.areaCode = areaCode;
        this.congestionLevel = congestionLevel;
        this.analysisMessage = analysisMessage;
        this.populationTime = populationTime;
    }
}
