package com.danburn.map.domain;

import com.danburn.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "events")
public class Event extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  private Long eventId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  @Column(name = "event_title", nullable = false, length = 200)
  private String eventTitle;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Builder
  public Event(Location location, String eventTitle, String description, LocalDateTime startTime, LocalDateTime endTime) {
    this.location = location;
    this.eventTitle = eventTitle;
    this.description = description;
    this.startTime = startTime;
    this.endTime = endTime;
  }
}
