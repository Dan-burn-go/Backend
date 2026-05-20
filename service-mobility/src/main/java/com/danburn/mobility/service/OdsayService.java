package com.danburn.mobility.service;

import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.TransitRouteResponse;
import com.danburn.mobility.infra.OdsayApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OdsayService {

    private final OdsayApiClient odsayApiClient;
    private final OdsayResponseMapper odsayResponseMapper;

    public TransitRouteResponse fetchOdsayRoute(OdsayApiRequest odsayApiRequest) {
        return odsayResponseMapper.toResponse(
                odsayApiClient.fetchOdsayRoute(odsayApiRequest)
        );
    }
}
