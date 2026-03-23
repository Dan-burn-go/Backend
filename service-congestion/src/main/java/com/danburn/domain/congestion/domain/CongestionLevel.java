package com.danburn.domain.congestion.domain;

import lombok.Getter;

@Getter
public enum CongestionLevel {
    RELAXED("여유"),
    NORMAL("보통"),
    SLIGHTLY_CROWDED("약간 붐빔"),
    BUSY("붐빔");

    private final String description;

    CongestionLevel(String description) {
          this.description = description;
      }
}