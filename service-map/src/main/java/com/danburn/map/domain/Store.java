package com.danburn.map.domain;

import com.danburn.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "stores")
public class Store extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "store_id")
  private Long storeId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  @Column(name = "store_name", nullable = false, length = 100)
  private String storeName;

  @Column(name = "category", length = 50)
  private String category;

  @Column(name = "address")
  private String address;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @lombok.Builder
  public Store(Location location, String storeName, String category, String address, Double latitude, Double longitude) {
    this.location = location;
    this.storeName = storeName;
    this.category = category;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
  }

}
