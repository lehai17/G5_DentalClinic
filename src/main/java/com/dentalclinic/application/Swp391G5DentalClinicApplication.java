package com.dentalclinic.application;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.dentalclinic")
@EntityScan(basePackages = "com.dentalclinic.model")
public class Swp391G5DentalClinicApplication {

    public static void main(String[] args) {
        SpringApplication.run(Swp391G5DentalClinicApplication.class, args);
    }



@Bean
CommandLineRunner run() {
        return args -> {
            System.out.println("======================================");
            System.out.println("  SWP391 G5 – DENTAL CLINIC STARTED!");
            System.out.println("  Server running at: http://localhost:8080");
            System.out.println("======================================");
        };
    }
}
