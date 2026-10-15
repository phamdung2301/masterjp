package com.masterjp.controller;

import com.masterjp.dto.AuthDtos;
import com.masterjp.dto.CommonDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Gui ma OTP 6 so (purpose: REGISTER | RESET_PASSWORD). */
    @PostMapping("/otp/request")
    public CommonDtos.MessageResponse requestOtp(@Valid @RequestBody AuthDtos.OtpRequest req) {
        authService.requestOtp(req.email(), req.purpose());
        return new CommonDtos.MessageResponse("Mã xác thực đã được gửi đến email của bạn.");
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/password/reset")
    public CommonDtos.MessageResponse resetPassword(@Valid @RequestBody AuthDtos.PasswordResetRequest req) {
        authService.resetPassword(req);
        return new CommonDtos.MessageResponse("Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.");
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal.getId());
    }
}
