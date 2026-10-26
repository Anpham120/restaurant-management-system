package com.restaurant.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RestaurantCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantCoreApplication.class, args);
    }
}
