package com.danburn.map.domain;

import com.danburn.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "locations")
public class Location extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "location_id")
  private Long locationId;

  @Column(name = "area_code", unique = true, nullable = false)
  private String apiAreaCode;

  @Column(name = "location_name", nullable = false, length = 50)
  private String locationName;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Builder
  public Location(String apiAreaCode, String locationName, Double latitude, Double longitude) {
    this.apiAreaCode = apiAreaCode;
    this.locationName = locationName;
    this.latitude = latitude;
    this.longitude = longitude;
  }
}
