package com.masterjp.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tu dong nap file .env dat canh thu muc chay (vd. backend/.env) truoc khi
 * Spring doc cau hinh — khong can thu vien ngoai.
 *
 * Do uu tien THAP NHAT (addLast): bien moi truong OS that luon thang .env.
 * Dang ky qua META-INF/spring.factories.
 */
public class DotenvEnvironmentListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Path envFile = Paths.get(System.getProperty("user.dir"), ".env");
        if (!Files.isRegularFile(envFile)) {
            return;
        }
        Map<String, Object> props = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    props.put(key, value);
                }
            }
        } catch (IOException e) {
            return;
        }
        if (!props.isEmpty()) {
            event.getEnvironment().getPropertySources().addLast(new MapPropertySource("dotenv", props));
        }
    }
}
