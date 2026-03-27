package com.danburn.congestion.domain;

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

    public static CongestionLevel fromDescription(String description) {
        String trimmed = description.trim();
        for (CongestionLevel level : values()) {
            if (level.description.equals(trimmed)) {
                return level;
            }
        }
        throw new IllegalArgumentException("알 수 없는 혼잡도 레벨: " + trimmed);
    }
}
