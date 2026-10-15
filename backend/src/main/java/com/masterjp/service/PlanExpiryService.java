package com.masterjp.service;

import com.masterjp.domain.Plan;
import com.masterjp.domain.Role;
import com.masterjp.domain.User;
import com.masterjp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Het han goi PREMIUM -> tu ve FREE.
 * - Check luc login + GET /api/auth/me (qua AuthService).
 * - Job quet moi gio.
 * Bo the da chia se cong khai GIU NGUYEN (khong giat lai), chi chan bat moi.
 */
@Service
public class PlanExpiryService {

    private static final Logger log = LoggerFactory.getLogger(PlanExpiryService.class);

    private final UserRepository userRepository;

    public PlanExpiryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Tra ve true neu vua ha goi (caller tu save). */
    public boolean applyIfExpired(User user) {
        if (user.getRole() == Role.LEARNER
                && user.getPlan() == Plan.PREMIUM
                && user.getPlanExpiresAt() != null
                && user.getPlanExpiresAt().before(new Date())) {
            user.setPlan(Plan.FREE);
            user.setPlanExpiresAt(null);
            return true;
        }
        return false;
    }

    @Scheduled(cron = "0 0 * * * *") // moi gio, phut 0
    public void expirePlansHourly() {
        List<User> expired = userRepository.findByPlanAndPlanExpiresAtLessThan(Plan.PREMIUM, new Date());
        for (User user : expired) {
            user.setPlan(Plan.FREE);
            user.setPlanExpiresAt(null);
        }
        if (!expired.isEmpty()) {
            userRepository.saveAll(expired);
            log.info("Da ha goi {} user PREMIUM het han ve FREE.", expired.size());
        }
    }
}
