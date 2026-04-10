package com.danburn.map.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {
  @Bean
  public RestClient restClient() {
    return RestClient.create();
    // 타임 아웃 관련 코드 추후 업데이트
  }
}
