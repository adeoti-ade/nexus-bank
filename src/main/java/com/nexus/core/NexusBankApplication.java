package com.nexus.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaAuditing
@org.springframework.scheduling.annotation.EnableAsync
public class NexusBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusBankApplication.class, args);
    }

}
