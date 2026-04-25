package com.danburn.map.domain;

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

  @Column(name = "category", nullable = false, length = 20)
  private String category;

  @Builder
  public Location(String apiAreaCode, String locationName, Double latitude, Double longitude, String category) {
    this.apiAreaCode = apiAreaCode;
    this.locationName = locationName;
    this.latitude = latitude;
    this.longitude = longitude;
    this.category = category;
  }
}
