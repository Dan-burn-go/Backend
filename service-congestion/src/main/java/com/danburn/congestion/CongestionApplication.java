package com.danburn.congestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@SpringBootApplication(scanBasePackages = {"com.danburn.congestion", "com.danburn.common"})
public class CongestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CongestionApplication.class, args);
    }
}
