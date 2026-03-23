package com.danburn.congestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {"com.danburn.congestion", "com.danburn.common", "com.danburn.domain"})
public class CongestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CongestionApplication.class, args);
    }
}
