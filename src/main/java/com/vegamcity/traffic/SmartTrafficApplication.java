package com.vegamcity.traffic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartTrafficApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartTrafficApplication.class, args);
    }
}
