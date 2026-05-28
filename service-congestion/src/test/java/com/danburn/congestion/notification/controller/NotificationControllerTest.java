package com.danburn.congestion.notification.controller;

import com.danburn.congestion.notification.config.VapidProperties;
import com.danburn.congestion.notification.dto.SubscribeResult;
import com.danburn.congestion.notification.exception.AlreadyNotBusyException;
import com.danburn.congestion.notification.exception.AreaNotFoundException;
import com.danburn.congestion.notification.service.SubscriptionService;
import com.danburn.congestion.domain.CongestionLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        })
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private VapidProperties vapidProperties;

    private static final String SUBSCRIBE_URL = "/api/v1/notifications/subscribe";
    private static final String UNSUBSCRIBE_URL = "/api/v1/notifications/unsubscribe";
    private static final String VAPID_KEY_URL = "/api/v1/notifications/vapid-public-key";

    private String subscribeBody(String areaCode) {
        return """
                {
                  "endpoint": "https://fcm.example.com/endpoint",
                  "keys": { "p256dh": "someKey", "auth": "someAuth" },
                  "areaCode": "%s"
                }
                """.formatted(areaCode);
    }

    @Nested
    @DisplayName("POST /subscribe")
    class Subscribe {

        @Test
        @DisplayName("새 구독 → 201 Created, status=SUBSCRIBED")
        void newSubscription() throws Exception {
            given(subscriptionService.subscribe(any()))
                    .willReturn(new SubscribeResult("SUBSCRIBED", Instant.now().plusSeconds(43200)));

            mockMvc.perform(post(SUBSCRIBE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(subscribeBody("POI001")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201));
        }

        @Test
        @DisplayName("기존 구독 갱신 → 200 OK, status=RENEWED")
        void renewSubscription() throws Exception {
            given(subscriptionService.subscribe(any()))
                    .willReturn(new SubscribeResult("RENEWED", Instant.now().plusSeconds(43200)));

            mockMvc.perform(post(SUBSCRIBE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(subscribeBody("POI001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("존재하지 않는 areaCode → 404")
        void areaNotFound() throws Exception {
            given(subscriptionService.subscribe(any()))
                    .willThrow(new AreaNotFoundException("POI999"));

            mockMvc.perform(post(SUBSCRIBE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(subscribeBody("POI999")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("이미 한가한 지역 → 409 Conflict")
        void alreadyNotBusy() throws Exception {
            given(subscriptionService.subscribe(any()))
                    .willThrow(new AlreadyNotBusyException(CongestionLevel.RELAXED));

            mockMvc.perform(post(SUBSCRIBE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(subscribeBody("POI001")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("유효성 검증 실패(endpoint 누락) → 400")
        void validationFail() throws Exception {
            String invalidBody = """
                    {
                      "keys": { "p256dh": "someKey", "auth": "someAuth" },
                      "areaCode": "POI001"
                    }
                    """;

            mockMvc.perform(post(SUBSCRIBE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /unsubscribe")
    class Unsubscribe {

        @Test
        @DisplayName("구독 취소 → 200 OK")
        void unsubscribeSuccess() throws Exception {
            willDoNothing().given(subscriptionService).unsubscribe(any(), any());

            String body = """
                    {
                      "endpoint": "https://fcm.example.com/endpoint",
                      "areaCode": "POI001"
                    }
                    """;

            mockMvc.perform(post(UNSUBSCRIBE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }
    }

    @Nested
    @DisplayName("GET /vapid-public-key")
    class VapidPublicKey {

        @Test
        @DisplayName("VAPID 키 설정됨 → 200 + Cache-Control 헤더")
        void keyConfigured() throws Exception {
            given(vapidProperties.publicKey()).willReturn("BExamplePublicKey");

            mockMvc.perform(get(VAPID_KEY_URL))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                    .andExpect(jsonPath("$.data.publicKey").value("BExamplePublicKey"));
        }

        @Test
        @DisplayName("VAPID 키 미설정(null) → 503 + Retry-After 헤더")
        void keyNotConfigured() throws Exception {
            given(vapidProperties.publicKey()).willReturn(null);

            mockMvc.perform(get(VAPID_KEY_URL))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.status").value(503));
        }

        @Test
        @DisplayName("VAPID 키 빈 문자열 → 503")
        void keyBlank() throws Exception {
            given(vapidProperties.publicKey()).willReturn("");

            mockMvc.perform(get(VAPID_KEY_URL))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
