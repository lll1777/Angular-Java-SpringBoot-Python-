package com.gov.serviceplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServicePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicePlatformApplication.class, args);
    }
}
