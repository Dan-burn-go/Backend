package com.danburn.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Nested
    @DisplayName("ok() 팩토리 메서드")
    class Ok {

        @Test
        @DisplayName("status 200, message OK, data 반환")
        void ok_반환값_검증() {
            ApiResponse<String> response = ApiResponse.ok("hello");

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getMessage()).isEqualTo("OK");
            assertThat(response.getData()).isEqualTo("hello");
        }

        @Test
        @DisplayName("data가 null이어도 정상 생성")
        void ok_data_null() {
            ApiResponse<String> response = ApiResponse.ok(null);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("created() 팩토리 메서드")
    class Created {

        @Test
        @DisplayName("status 201, message Created, data 반환")
        void created_반환값_검증() {
            ApiResponse<Integer> response = ApiResponse.created(42);

            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(response.getMessage()).isEqualTo("Created");
            assertThat(response.getData()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("error() 팩토리 메서드")
    class Error {

        @Test
        @DisplayName("지정한 status와 message 반환, data는 null")
        void error_반환값_검증() {
            ApiResponse<Void> response = ApiResponse.error(400, "잘못된 요청");

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("잘못된 요청");
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("500 서버 오류 케이스")
        void error_서버_오류() {
            ApiResponse<Void> response = ApiResponse.error(500, "Internal Server Error");

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("Internal Server Error");
        }
    }
}
