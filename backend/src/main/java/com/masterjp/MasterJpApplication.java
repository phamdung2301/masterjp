package com.masterjp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.masterjp.config.DotenvEnvironmentListener;

@SpringBootApplication
@EnableScheduling // job het han goi PREMIUM moi gio (PlanExpiryService)
public class MasterJpApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MasterJpApplication.class);
        // Nap backend/.env truoc khi Spring doc cau hinh.
        // Dang ky truc tiep o day (khong phu thuoc META-INF/spring.factories
        // von deprecated) — chac chan chay tren moi phien ban Spring Boot.
        app.addListeners(new DotenvEnvironmentListener());
        app.run(args);
    }
}
