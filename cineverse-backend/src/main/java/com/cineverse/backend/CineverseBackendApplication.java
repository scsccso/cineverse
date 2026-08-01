package com.cineverse.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CineverseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineverseBackendApplication.class, args);
    }
}
