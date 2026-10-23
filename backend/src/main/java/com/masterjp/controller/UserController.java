package com.masterjp.controller;

import com.masterjp.dto.UserDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase D — F-046: cai dat ca nhan.
 * <p>
 * PATCH /api/users/me — body {"emailReminderOptIn": boolean}: user tu bat/tat
 * email nhac on tap 8:00 sang cua chinh minh. (Lua chon chinh theo DOC-13 muc 9;
 * GET /api/auth/me cung tra them field emailReminderOptIn de FE khoi tao toggle.)
 * Can JWT (roi vao rule /api/** authenticated o SecurityConfig).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/me")
    public UserDtos.MySettingsResponse updateMySettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UserDtos.UpdateMySettingsRequest req) {
        Boolean optIn = req == null ? null : req.emailReminderOptIn();
        return userService.updateMySettings(principal.getId(), optIn);
    }
}
