package org.example.handkerskinsproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HandkerSkinsProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandkerSkinsProjectApplication.class, args);
    }
}
