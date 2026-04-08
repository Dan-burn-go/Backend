package com.danburn.map.dto.request;

import lombok.Builder;
import lombok.Getter;

@Getter
public class SeoulCultureInfoApiRequest {
  private final String type = "json";
  private final String service = "culturalEventInfo";
  private final int startIndex;
  private final int endIndex;
  private final String title;

  @Builder
  public SeoulCultureInfoApiRequest(int startIndex, int endIndex, String title){
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.title = (title != null && !title.isBlank()) ? title : " ";
  }
}
