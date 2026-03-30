package com.danburn.map.domain;

import com.danburn.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "congestions")
public class Congestion extends BaseEntity {
  @Id
  @Column(name = "location_id", nullable = false, unique = true)
  private Long locationId;

  @OneToOne
  @MapsId
  @JoinColumn(name="location_id")
  private Location location;

  @Column(name = "congestion_level")
  private Integer congestionLevel;

  @Builder
  public Congestion(Long locationId, Location location, Integer congestionLevel){
    this.locationId = locationId;
    this.location = location;
    this.congestionLevel = congestionLevel;
  }
}
