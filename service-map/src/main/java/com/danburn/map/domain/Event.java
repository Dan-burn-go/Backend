package com.danburn.map.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "events", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_event_title_place_start_date",
        columnNames = {"event_title", "place", "start_date"}
    )
})
public class Event extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  private Long eventId;

  @Column(name = "event_title", nullable = false, length = 200)
  private String eventTitle;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "codename", length = 50)
  private String codename;

  @Column(name = "place", length = 200)
  private String place;

  @Column(name = "use_fee", length = 200)
  private String useFee;

  @Column(name = "inquiry", length = 200)
  private String inquiry;

  @Column(name = "org_link", length = 1000)
  private String orgLink;

  @Column(name = "main_img", length = 1000)
  private String mainImg;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Builder
  public Event(String eventTitle, String description,
               LocalDate startDate, LocalDate endDate, String codename,
               String place, String useFee, String inquiry, String orgLink, 
               String mainImg, Double latitude, Double longitude) {
    this.eventTitle = eventTitle;
    this.description = description;
    this.startDate = startDate;
    this.endDate = endDate;
    this.codename = codename;
    this.place = place;
    this.useFee = useFee;
    this.inquiry = inquiry;
    this.orgLink = orgLink;
    this.mainImg = mainImg;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  public void updateDetails(String description, LocalDate endDate, String codename,
                            String useFee, String inquiry, String orgLink, 
                            String mainImg, Double latitude, Double longitude) {
    this.description = description;
    this.endDate = endDate;
    this.codename = codename;
    this.useFee = useFee;
    this.inquiry = inquiry;
    this.orgLink = orgLink;
    this.mainImg = mainImg;
    this.latitude = latitude;
    this.longitude = longitude;
  }
}
