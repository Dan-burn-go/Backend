package com.danburn.map.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KakaoLocalApiResponse(
  Meta meta,
  List<Document> documents
) {
  public  record Meta(
    @JsonProperty("total_count") int totalCount,
    @JsonProperty("is_end") boolean isEnd
  ){}

  public record Document(
    @JsonProperty("place_name") String placeName,
    @JsonProperty("category_name") String categoryName,
    @JsonProperty("address_name") String addressName,
    @JsonProperty("road_address_name") String roadAddressName,
    @JsonProperty("place_url") String placeUrl,
    String distance,
    String x,
    String y
  ) {}
}
