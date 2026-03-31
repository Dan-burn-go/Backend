package com.danburn.map.domain;

import com.danburn.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(AlternativeLocationId.class)
@Table(name = "alternative_locations")
public class AlternativeLocation extends BaseEntity {

  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "alternative_location_id", nullable = false)
  private Location alternativeLocation;

  @Column(name = "priority")
  private Integer priority;

  @Builder
  public AlternativeLocation(Location location, Location alternativeLocation, Integer priority) {
    this.location = location;
    this.alternativeLocation = alternativeLocation;
    this.priority = priority;
  }
}
