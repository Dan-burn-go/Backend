package com.danburn.congestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class CongestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CongestionApplication.class, args);
    }
}
