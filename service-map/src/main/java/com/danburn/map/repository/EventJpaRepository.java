package com.danburn.map.repository;

import com.danburn.map.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventJpaRepository extends JpaRepository<Event, Long> {

  @Query(value = "SELECT * FROM events e " +
                 "WHERE ST_Distance_Sphere(POINT(e.longitude, e.latitude), POINT(:longitude, :latitude)) <= :radiusMeter " +
                 "ORDER BY ST_Distance_Sphere(POINT(e.longitude, e.latitude), POINT(:longitude, :latitude)) ASC", nativeQuery = true)
  List<Event> findEventsWithinRadius(

    @Param("latitude") Double latitude,
    @Param("longitude") Double longitude,
    @Param("radiusMeter") Double radiusMeter);

  void deleteByEndDateBefore(LocalDate today);

  Optional<Event> findByEventTitleAndPlaceAndStartDate(String eventTitle, String place, LocalDate startDate);
}