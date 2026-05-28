package com.danburn.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorsProperties")
class CorsPropertiesTest {

    @Nested
    @DisplayName("allowedOrigins")
    class AllowedOrigins {

        @Test
        @DisplayName("setAllowedOrigins 후 getAllowedOrigins 반환 일치")
        void origins_설정_조회() {
            CorsProperties props = new CorsProperties();
            List<String> origins = List.of("https://example.com", "https://api.example.com");

            props.setAllowedOrigins(origins);

            assertThat(props.getAllowedOrigins()).containsExactlyElementsOf(origins);
        }

        @Test
        @DisplayName("설정 전 기본값은 null")
        void origins_기본값_null() {
            CorsProperties props = new CorsProperties();

            assertThat(props.getAllowedOrigins()).isNull();
        }
    }

    @Nested
    @DisplayName("allowedMethods")
    class AllowedMethods {

        @Test
        @DisplayName("setAllowedMethods 후 getAllowedMethods 반환 일치")
        void methods_설정_조회() {
            CorsProperties props = new CorsProperties();
            List<String> methods = List.of("GET", "POST", "PUT", "DELETE");

            props.setAllowedMethods(methods);

            assertThat(props.getAllowedMethods()).containsExactlyElementsOf(methods);
        }

        @Test
        @DisplayName("설정 전 기본값은 null")
        void methods_기본값_null() {
            CorsProperties props = new CorsProperties();

            assertThat(props.getAllowedMethods()).isNull();
        }
    }

    @Nested
    @DisplayName("allowedHeaders")
    class AllowedHeaders {

        @Test
        @DisplayName("setAllowedHeaders 후 getAllowedHeaders 반환 일치")
        void headers_설정_조회() {
            CorsProperties props = new CorsProperties();
            List<String> headers = List.of("Content-Type", "Authorization");

            props.setAllowedHeaders(headers);

            assertThat(props.getAllowedHeaders()).containsExactlyElementsOf(headers);
        }

        @Test
        @DisplayName("설정 전 기본값은 null")
        void headers_기본값_null() {
            CorsProperties props = new CorsProperties();

            assertThat(props.getAllowedHeaders()).isNull();
        }
    }
}
