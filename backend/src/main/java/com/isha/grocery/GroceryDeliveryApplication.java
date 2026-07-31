package com.isha.grocery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GroceryDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroceryDeliveryApplication.class, args);
    }
}
