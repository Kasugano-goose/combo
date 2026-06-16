package com.example.combo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ComboApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComboApplication.class, args);
    }

}
