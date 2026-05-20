package com.danburn.congestion.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubscribeRequest(
        @NotBlank @Size(max = 700) @Pattern(regexp = "^https://.+") String endpoint,
        @NotNull @Valid Keys keys,
        @NotBlank @Size(max = 20) String areaCode
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
