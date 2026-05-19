package com.danburn.mobility.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OdsayApiClient {
  private final RestClient OdsayRestClient;
}
