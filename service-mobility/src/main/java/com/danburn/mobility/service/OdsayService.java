package com.danburn.mobility.service;

import com.danburn.common.exception.GlobalException;
import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import com.danburn.mobility.infra.OdsayApiClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OdsayService {

    private final OdsayApiClient odsayApiClient;
    private final OdsayResponseMapper odsayResponseMapper;

    @CircuitBreaker(name = "odsay", fallbackMethod = "fetchOdsayRouteFallback")
    @Retry(name = "odsay")
    public TransitRouteResponse fetchOdsayRoute(OdsayApiRequest odsayApiRequest) {
        return odsayResponseMapper.toResponse(
                odsayApiClient.fetchOdsayRoute(odsayApiRequest)
        );
    }

    TransitRouteResponse fetchOdsayRouteFallback(OdsayApiRequest odsayApiRequest, Throwable t) {
        if (t instanceof GlobalException e) {
            throw e;
        }
        log.warn("ODsay 서킷 브레이커 작동 - fallback 실행: {}", t.getMessage());
        throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "대중교통 경로 조회 서비스가 일시적으로 불가합니다.");
    }
}
