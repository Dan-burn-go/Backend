package com.danburn.congestion;

import com.danburn.congestion.notification.config.VapidProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(VapidProperties.class)
@SpringBootApplication(scanBasePackages = {"com.danburn.congestion", "com.danburn.common"})
public class CongestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CongestionApplication.class, args);
    }
}
