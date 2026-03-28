package com.danburn.map.repository;

import com.danburn.map.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationJpaRepository extends JpaRepository<Location, Long> {
  Optional<Location> findByApiAreaCode(String apiAreaCode);
  List<Location> findByLocationName(String locationName);
}
