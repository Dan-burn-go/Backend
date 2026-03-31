package com.danburn.congestion.repository;

import com.danburn.congestion.dto.CongestionRedisDto;

import java.util.List;
import java.util.Optional;

public interface CongestionRedisRepository {

    void save(CongestionRedisDto dto);

    Optional<CongestionRedisDto> findByAreaCode(String areaCode);

    List<CongestionRedisDto> findAll();

    void saveAll(List<CongestionRedisDto> dtos);

    void delete(String areaCode);
}
