package com.danburn.map.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SeoulCultureInfoApiResponse(
        @JsonProperty("culturalEventInfo") CulturalEventInfo culturalEventInfo
) {
    public record CulturalEventInfo(
            @JsonProperty("list_total_count") Integer listTotalCount,
            @JsonProperty("row") List<Row> row
    ) {}

    public record Row(
            @JsonProperty("TITLE") String title,
            @JsonProperty("STRTDATE") String startDate,
            @JsonProperty("END_DATE") String endDate,
            @JsonProperty("CODENAME") String codename,
            @JsonProperty("PLACE") String place,
            @JsonProperty("USE_FEE") String useFee,
            @JsonProperty("INQUIRY") String inquiry,
            @JsonProperty("PROGRAM") String description,
            @JsonProperty("ORG_LINK") String orgLink,
            @JsonProperty("MAIN_IMG") String mainImg,
            @JsonProperty("LAT") Double latitude,
            @JsonProperty("LOT") Double longitude
    ) {}
}