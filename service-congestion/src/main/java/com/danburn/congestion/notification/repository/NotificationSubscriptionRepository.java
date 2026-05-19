package com.danburn.congestion.notification.repository;

import com.danburn.congestion.notification.domain.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    Optional<NotificationSubscription> findByEndpointAndAreaCode(String endpoint, String areaCode);

    List<NotificationSubscription> findByAreaCodeAndExpiresAtAfter(String areaCode, Instant now);

    @Modifying
    @Transactional
    void deleteByEndpointAndAreaCode(String endpoint, String areaCode);

    @Modifying
    @Transactional
    int deleteByExpiresAtBefore(Instant now);
}
