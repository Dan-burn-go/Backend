package com.danburn.congestion.notification.dto;

import java.time.Instant;

public record SubscribeResponse(
        String status,
        Instant expiresAt
) {}
