package com.danburn.congestion.domain;

import lombok.Getter;

@Getter
public enum PopulationTrend {
    INCREASING("증가"),
    DECREASING("감소"),
    KEEPING("유지");

    private final String description;

    PopulationTrend(String description) {
          this.description = description;
      }
}