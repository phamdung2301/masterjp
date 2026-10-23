package com.masterjp.service;

import com.masterjp.domain.User;
import com.masterjp.dto.UserDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Cai dat ca nhan cua user (Phase D — F-046: toggle email nhac on tap). */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * PATCH /api/users/me — user tu cap nhat emailReminderOptIn cua chinh minh.
     * userId lay tu @AuthenticationPrincipal, khong tin field user trong request.
     */
    public UserDtos.MySettingsResponse updateMySettings(String userId, Boolean emailReminderOptIn) {
        if (emailReminderOptIn == null) {
            throw new ApiException("VALIDATION_ERROR",
                    "emailReminderOptIn không được để trống.", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));
        user.setEmailReminderOptIn(emailReminderOptIn);
        userRepository.save(user);
        return new UserDtos.MySettingsResponse(user.getId(), user.getEmail(), user.getName(),
                user.getRole().name(), user.getPlan().name(),
                user.getPlanExpiresAt(), user.getStreakDays(),
                user.isEmailReminderOptIn());
    }
}
