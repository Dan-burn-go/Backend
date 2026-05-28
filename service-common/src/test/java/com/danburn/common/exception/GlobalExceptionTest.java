package com.danburn.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalException")
class GlobalExceptionTest {

    @Nested
    @DisplayName("생성자")
    class Constructor {

        @Test
        @DisplayName("status와 message가 올바르게 저장됨")
        void status_message_저장() {
            GlobalException ex = new GlobalException(404, "리소스를 찾을 수 없음");

            assertThat(ex.getStatus()).isEqualTo(404);
            assertThat(ex.getMessage()).isEqualTo("리소스를 찾을 수 없음");
        }

        @Test
        @DisplayName("RuntimeException 상속 확인")
        void RuntimeException_상속() {
            GlobalException ex = new GlobalException(500, "서버 오류");

            assertThat(ex).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("400 상태코드 케이스")
        void status_400() {
            GlobalException ex = new GlobalException(400, "잘못된 요청");

            assertThat(ex.getStatus()).isEqualTo(400);
            assertThat(ex.getMessage()).isEqualTo("잘못된 요청");
        }

        @Test
        @DisplayName("403 상태코드 케이스")
        void status_403() {
            GlobalException ex = new GlobalException(403, "접근 권한 없음");

            assertThat(ex.getStatus()).isEqualTo(403);
            assertThat(ex.getMessage()).isEqualTo("접근 권한 없음");
        }
    }
}
