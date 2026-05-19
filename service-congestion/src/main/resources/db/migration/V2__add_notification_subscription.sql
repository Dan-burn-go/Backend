CREATE TABLE notification_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    endpoint VARCHAR(700) NOT NULL,
    p256dh_key VARCHAR(200) NOT NULL,
    auth_key VARCHAR(100) NOT NULL,
    area_code VARCHAR(20) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_endpoint_area (endpoint, area_code),
    KEY idx_subscription_area_expires (area_code, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
