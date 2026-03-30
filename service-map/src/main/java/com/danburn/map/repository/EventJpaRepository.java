package com.danburn.map.repository;

import com.danburn.map.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventJpaRepository extends JpaRepository<Event, Long> {
  Optional<Event> findByEventTitle(String eventTitle);

  List<Event> findByLocationLocationId(Long locationId);

  @Query("SELECT e FROM Event e WHERE e.endTime >= :currentTime OR e.endTime IS NULL ORDER BY e.startTime ASC")
  List<Event> findActiveOrUpcomingEvents(@Param("currentTime") LocalDateTime currentTime);

  @Query("SELECT e FROM Event e WHERE e.startTime <= :end AND (e.endTime IS NULL OR e.endTime >= :start)")
  List<Event> findEventsInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
