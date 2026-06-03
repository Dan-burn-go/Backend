package com.danburn.mobility.infra;

import com.danburn.common.exception.GlobalException;
import com.danburn.mobility.dto.request.OdsayApiRequest;
import com.danburn.mobility.dto.response.OdsayApiResponse;
import com.danburn.mobility.exception.OdsayServerException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class OdsayApiClient {
  private final RestClient odsayRestClient;

  @Value("${odsay.api.key}")
  private String odsayApiKey;

  public OdsayApiResponse fetchOdsayRoute(OdsayApiRequest odsayApiRequest){
    OdsayApiResponse response;
    try {
      response = odsayRestClient.get()
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
    } catch (RestClientException e) {
      throw new OdsayServerException("ODsay API 호출 실패: " + e.getMessage());
    }
    if (response == null) {
      throw new OdsayServerException("ODsay API 응답이 없습니다.");
    }
    if (response.result() == null) {
      throw new GlobalException(HttpStatus.SERVICE_UNAVAILABLE.value(), "경로를 찾을 수 없습니다.");
    }
    return response;
  }
}
