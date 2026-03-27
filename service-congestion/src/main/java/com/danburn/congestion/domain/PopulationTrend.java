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

    public static PopulationTrend fromDescription(String description) {
        String trimmed = description.trim();
        for (PopulationTrend trend : values()) {
            if (trend.description.equals(trimmed)) {
                return trend;
            }
        }
        throw new IllegalArgumentException("알 수 없는 인구 추세: " + trimmed);
    }
}
