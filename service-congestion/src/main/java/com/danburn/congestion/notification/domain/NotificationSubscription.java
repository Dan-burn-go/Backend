package com.danburn.congestion.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "notification_subscription",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subscription_endpoint_area", columnNames = {"endpoint", "area_code"})
        },
        indexes = {
                @Index(name = "idx_subscription_area_expires", columnList = "area_code, expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint", nullable = false, length = 700)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 200)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 100)
    private String authKey;

    @Column(name = "area_code", nullable = false, length = 20)
    private String areaCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private NotificationSubscription(
            String endpoint,
            String p256dhKey,
            String authKey,
            String areaCode,
            Instant expiresAt,
            Instant createdAt) {
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.areaCode = areaCode;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void renewExpiresAt(Instant newExpiresAt) {
        this.expiresAt = newExpiresAt;
    }
}
