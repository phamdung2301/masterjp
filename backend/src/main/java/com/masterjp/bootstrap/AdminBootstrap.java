package com.masterjp.bootstrap;

import com.masterjp.domain.Plan;
import com.masterjp.domain.Role;
import com.masterjp.domain.User;
import com.masterjp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Tao tai khoan admin duy nhat tu ENV (FR-005) khi backend khoi dong.
 *
 * - Chi tao khi CHUA co user voi ADMIN_EMAIL.
 * - KHONG reset mat khau khi restart.
 * - KHONG tu nang quyen user thuong.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String adminEmail,
                          @Value("${app.admin.password:}") String adminPassword,
                          @Value("${app.admin.name:Master JP Admin}") String adminName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Chua cau hinh ADMIN_EMAIL — bo qua tao tai khoan admin.");
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.info("Tai khoan admin ({}) da ton tai — giu nguyen, khong reset mat khau.", email);
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Chua cau hinh ADMIN_PASSWORD — khong the tao tai khoan admin ({}).", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setName(adminName != null && !adminName.isBlank() ? adminName.trim() : "Admin");
        admin.setRole(Role.ADMIN);
        admin.setPlan(Plan.FREE); // plan chi ap dung cho LEARNER; ADMIN bypass moi gate
        admin.setEmailVerified(true);
        userRepository.save(admin);
        log.info("Da tao tai khoan admin: {}", email);
    }
}
