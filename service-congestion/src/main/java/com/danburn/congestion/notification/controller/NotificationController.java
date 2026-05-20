package com.danburn.congestion.notification.controller;

import com.danburn.common.response.ApiResponse;
import com.danburn.congestion.notification.config.VapidProperties;
import com.danburn.congestion.notification.dto.SubscribeRequest;
import com.danburn.congestion.notification.dto.SubscribeResult;
import com.danburn.congestion.notification.dto.UnsubscribeRequest;
import com.danburn.congestion.notification.dto.VapidKeyResponse;
import com.danburn.congestion.notification.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SubscriptionService subscriptionService;
    private final VapidProperties vapidProperties;

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<SubscribeResult>> subscribe(@RequestBody @Valid SubscribeRequest request) {
        SubscribeResult result = subscriptionService.subscribe(request);

        if ("SUBSCRIBED".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@Valid @RequestBody UnsubscribeRequest req) {
        subscriptionService.unsubscribe(req.endpoint(), req.areaCode());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/vapid-public-key")
    public ResponseEntity<ApiResponse<VapidKeyResponse>> getVapidPublicKey() {
        if (vapidProperties.publicKey() == null || vapidProperties.publicKey().isBlank()) {
            ApiResponse<VapidKeyResponse> errorBody = new ApiResponse<>(503, "VAPID 키가 설정되지 않았습니다", null);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "3600")
                    .body(errorBody);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(ApiResponse.ok(new VapidKeyResponse(vapidProperties.publicKey())));
    }
}
