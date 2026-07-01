package com.danburn.mobility.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {
  @Bean
  public RestClient odsayRestClient(){
    SimpleClientHttpRequestFactory simpleClientHttpRequestFactory = new SimpleClientHttpRequestFactory();
    simpleClientHttpRequestFactory.setConnectTimeout(Duration.ofSeconds(2));
    simpleClientHttpRequestFactory.setReadTimeout(Duration.ofSeconds(3));

    return RestClient.builder()
      .requestFactory(simpleClientHttpRequestFactory)
      .baseUrl("https://api.odsay.com/v1/api")
      .defaultHeader("Referer", "https://goseoul.today")
      .build();
  }
}
