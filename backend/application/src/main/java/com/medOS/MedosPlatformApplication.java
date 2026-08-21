package com.medOS;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.medOS")
public class MedosPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedosPlatformApplication.class, args);
    }
}

