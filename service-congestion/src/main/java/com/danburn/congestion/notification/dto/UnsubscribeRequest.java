package com.danburn.congestion.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnsubscribeRequest(
        @NotBlank @Size(max = 700) String endpoint,
        @NotBlank @Size(max = 20) String areaCode
) {}
