package com.danburn.congestion.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("vapid")
public record VapidProperties(
        String publicKey,
        String privateKey,
        String subject
) {}
