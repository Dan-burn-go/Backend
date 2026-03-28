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
public class AlternativeLocation extends BaseEntity {

  @Id
  @Column(name = "location_id", nullable = false)
  private Long locationId;

  @Id
  @Column(name = "alternative_location_id", nullable = false)
  private Long alternativeLocationId;

  @Column(name = "priority")
  private Integer priority;

  @Builder
  public AlternativeLocation(Long locationId, Long alternativeLocationId, Integer priority) {
    this.locationId = locationId;
    this.alternativeLocationId = alternativeLocationId;
    this.priority = priority;
  }
}
