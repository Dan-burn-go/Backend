package com.danburn.domain.congestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import lombok.AccessLevel;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;
import com.danburn.common.domain.BaseEntity;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Congestion extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "congestion_id")
    private Long id;


    @Column(name = "location_id")
    private Long locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level")
    private CongestionLevel congestionLevel;

    @Column(name = "min_people_count")
    private Integer minPeopleCount;

    @Column(name = "max_people_count")
    private Integer maxPeopleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "population_trend")
    private PopulationTrend populationTrend;

    @Builder
    private Congestion(
                       Long locationId, 
                       CongestionLevel congestionLevel, 
                       Integer minPeopleCount, 
                       Integer maxPeopleCount, 
                       PopulationTrend populationTrend){
        this.locationId = locationId;
        this.congestionLevel = congestionLevel;
        this.minPeopleCount = minPeopleCount;
        this.maxPeopleCount = maxPeopleCount;
        this.populationTrend = populationTrend;
    }
}