package com.danburn.congestion.notification.dto;

import java.time.Instant;

public record SubscribeResult(String status, Instant expiresAt) {}
