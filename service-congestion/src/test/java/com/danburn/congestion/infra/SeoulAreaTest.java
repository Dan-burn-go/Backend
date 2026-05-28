package com.danburn.congestion.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulAreaTest {

    @Nested
    @DisplayName("getCode / getName")
    class GetCodeName {

        @Test
        @DisplayName("각 enum 상수의 code·name 접근 가능")
        void codeAndName() {
            assertThat(SeoulArea.POI001.getCode()).isEqualTo("POI001");
            assertThat(SeoulArea.POI001.getName()).isEqualTo("강남 MICE 관광특구");
        }

        @Test
        @DisplayName("code와 enum name이 일치")
        void codeMatchesEnumName() {
            for (SeoulArea area : SeoulArea.values()) {
                assertThat(area.getCode()).isEqualTo(area.name());
            }
        }
    }

    @Nested
    @DisplayName("all()")
    class All {

        @Test
        @DisplayName("all() 목록이 비어있지 않음")
        void allNotEmpty() {
            List<SeoulArea> all = SeoulArea.all();
            assertThat(all).isNotEmpty();
        }

        @Test
        @DisplayName("all() 크기가 values() 크기와 동일")
        void allSizeMatchesValues() {
            assertThat(SeoulArea.all()).hasSize(SeoulArea.values().length);
        }

        @Test
        @DisplayName("all()에 중복 없음")
        void noDuplicates() {
            List<SeoulArea> all = SeoulArea.all();
            long distinct = all.stream().map(SeoulArea::getCode).distinct().count();
            assertThat(distinct).isEqualTo(all.size());
        }
    }
}
