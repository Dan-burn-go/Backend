package com.danburn.congestion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CongestionLevelTest {

    @ParameterizedTest
    @CsvSource({
            "여유, RELAXED",
            "보통, NORMAL",
            "약간 붐빔, SLIGHTLY_CROWDED",
            "붐빔, BUSY"
    })
    @DisplayName("fromDescription: 유효한 설명 → 올바른 enum 반환")
    void fromDescription_validDescription(String description, CongestionLevel expected) {
        assertThat(CongestionLevel.fromDescription(description)).isEqualTo(expected);
    }

    @Test
    @DisplayName("fromDescription: 앞뒤 공백이 있어도 정상 변환")
    void fromDescription_trimmedDescription() {
        assertThat(CongestionLevel.fromDescription("  여유  ")).isEqualTo(CongestionLevel.RELAXED);
    }

    @Test
    @DisplayName("fromDescription: 알 수 없는 설명 → IllegalArgumentException")
    void fromDescription_invalidDescription() {
        assertThatThrownBy(() -> CongestionLevel.fromDescription("알 수 없음"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 혼잡도 레벨");
    }

    @Test
    @DisplayName("getDescription: enum → 한글 설명 반환")
    void getDescription() {
        assertThat(CongestionLevel.BUSY.getDescription()).isEqualTo("붐빔");
    }
}
