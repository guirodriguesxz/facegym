package com.facegym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FaceGymApplication {
    public static void main(String[] args) {
        SpringApplication.run(FaceGymApplication.class, args);
    }
}
