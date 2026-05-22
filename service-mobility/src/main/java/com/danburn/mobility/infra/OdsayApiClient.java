package com.danburn.mobility.infra;

import com.danburn.common.exception.GlobalException;
import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.OdsayApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class OdsayApiClient {
  private final RestClient odsayRestClient;

  @Value("${odsay.api.key}")
  private String odsayApiKey;

  public OdsayApiResponse fetchOdsayRoute(OdsayApiRequest odsayApiRequest){
    OdsayApiResponse response = odsayRestClient.get()
      .uri(uriBuilder -> uriBuilder
        .path("/searchPubTransPathT")
        .queryParam("apiKey",odsayApiKey)
        .queryParam("SX",odsayApiRequest.originLng())
        .queryParam("SY",odsayApiRequest.originLat())
        .queryParam("EX",odsayApiRequest.destLng())
        .queryParam("EY",odsayApiRequest.destLat())
        .queryParam("SearchType",0)
        .build())
      .retrieve()
      .body(OdsayApiResponse.class);
    if (response == null || response.result() == null) {
      throw new GlobalException(HttpStatus.BAD_GATEWAY.value(), "ODsay API 응답이 올바르지 않습니다.");
    }
    return response;
  }
}
