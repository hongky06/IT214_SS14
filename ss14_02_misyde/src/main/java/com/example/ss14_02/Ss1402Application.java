package com.example.ss14_02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Ss1402Application {

    public static void main(String[] args) {
        SpringApplication.run(Ss1402Application.class, args);
    }

}
