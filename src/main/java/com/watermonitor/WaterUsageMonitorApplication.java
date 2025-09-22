package com.watermonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WaterUsageMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaterUsageMonitorApplication.class, args);
    }
}