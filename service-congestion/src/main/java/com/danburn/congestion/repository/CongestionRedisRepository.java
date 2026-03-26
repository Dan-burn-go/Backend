package com.danburn.congestion.repository;

import com.danburn.congestion.dto.CongestionRedisDto;

import java.util.List;
import java.util.Optional;

public interface CongestionRedisRepository {

    void save(CongestionRedisDto dto);

    Optional<CongestionRedisDto> findByLocationId(Long locationId);

    List<CongestionRedisDto> findAll();

    void delete(Long locationId);
}
