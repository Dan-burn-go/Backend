package com.danburn.map.infra;

import com.danburn.map.dto.response.KakaoLocalApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class KakaoLocalApiClient {

  private final RestClient restClient;

  public KakaoLocalApiClient(@Value("${kakao.api.key}") String apiKey) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(5));

    this.restClient = RestClient.builder()
      .baseUrl("https://dapi.kakao.com")
      .defaultHeader("Authorization", "KakaoAK " + apiKey)
      .requestFactory(factory)
      .build();
  }

  public KakaoLocalApiResponse fetchNearbyPlaces(String categoryCode, double longitude, double latitude) {
    return restClient.get()
      .uri(uriBuilder -> uriBuilder
        .path("/v2/local/search/category.json")
        .queryParam("category_group_code", categoryCode)
        .queryParam("x", longitude)
        .queryParam("y", latitude)
        .queryParam("radius", 1000)
        .queryParam("page", 10)
        .build())
      .retrieve()
      .body(KakaoLocalApiResponse.class);
  }
}
