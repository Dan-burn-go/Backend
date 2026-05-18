package com.danburn.congestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "congestion",
        indexes = {
                @Index(name = "idx_congestion_area_code_created_at", columnList = "area_code, created_at"),
                @Index(name = "idx_congestion_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Congestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "congestion_id")
    private Long id;

    @Column(name = "area_code", nullable = false, length = 20)
    private String areaCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level", nullable = false)
    private CongestionLevel congestionLevel;

    @Column(name = "congestion_message", length = 500)
    private String congestionMessage;

    @Column(name = "min_people_count")
    private Integer minPeopleCount;

    @Column(name = "max_people_count")
    private Integer maxPeopleCount;

    @Column(name = "population_time")
    private LocalDateTime populationTime;

    @Column(name = "congestion_forecast", columnDefinition = "json")
    private String forecast;

    @Builder
    private Congestion(
            String areaCode,
            CongestionLevel congestionLevel,
            String congestionMessage,
            Integer minPeopleCount,
            Integer maxPeopleCount,
            LocalDateTime populationTime,
            String forecast) {
        this.areaCode = areaCode;
        this.congestionLevel = congestionLevel;
        this.congestionMessage = congestionMessage;
        this.minPeopleCount = minPeopleCount;
        this.maxPeopleCount = maxPeopleCount;
        this.populationTime = populationTime;
        this.forecast = forecast;
    }
}
