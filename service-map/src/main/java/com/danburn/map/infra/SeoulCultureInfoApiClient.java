package com.danburn.map.infra;

import com.danburn.map.dto.request.SeoulCultureInfoApiRequest;
import com.danburn.map.dto.response.SeoulCultureInfoApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class SeoulCultureInfoApiClient implements SeoulApiClient{
  private final RestClient restClient;

  @Value("${seoul.api.key}")
  private String apiKey;

  private static final String URL_TEMPLATE =
    "http://openAPI.seoul.go.kr:8088/{apiKey}/{type}/{service}/{startIndex}/{endIndex}/{title}";

  public SeoulCultureInfoApiResponse fetchEvents(SeoulCultureInfoApiRequest request){
    return restClient.get()
            .uri(URL_TEMPLATE,
                 apiKey,
                 request.getType(),
                 request.getService(),
                 request.getStartIndex(),
                 request.getEndIndex(),
                 request.getTitle())
            .retrieve()
            .body(SeoulCultureInfoApiResponse.class);
  }
}
