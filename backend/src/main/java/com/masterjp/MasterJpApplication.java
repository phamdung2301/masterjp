package com.masterjp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // job het han goi PREMIUM moi gio (PlanExpiryService)
public class MasterJpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterJpApplication.class, args);
    }
}
