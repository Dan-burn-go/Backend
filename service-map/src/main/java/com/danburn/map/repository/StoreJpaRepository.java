package com.danburn.map.repository;

import com.danburn.map.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreJpaRepository extends JpaRepository<Store, Long> {
  List<Store> findByStoreName(String storeName);
  List<Store> findByLocationLocationId(Long locationId);
  Optional<Store> findByAddress(String address);
  List<Store> findByCategory(String category);
}
