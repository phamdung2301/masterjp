package com.masterjp.service;

import com.masterjp.domain.OtpPurpose;
import com.masterjp.domain.Plan;
import com.masterjp.domain.Role;
import com.masterjp.domain.User;
import com.masterjp.dto.AuthDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.UserRepository;
import com.masterjp.security.JwtUtil;
import com.masterjp.util.EnumUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Auth (F-001—F-004): OTP -> dang ky / dang nhap (JWT 24h) / quen mat khau.
 * Response LUON dung field "accessToken" (contract tuyet doi voi FE).
 */
@Service
public class AuthService {

    private static final long LOCK_DURATION_MS = 15 * 60_000; // 15 phut
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PlanExpiryService planExpiryService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, OtpService otpService,
                       PlanExpiryService planExpiryService, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.planExpiryService = planExpiryService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public void requestOtp(String email, String purposeStr) {
        OtpPurpose purpose = EnumUtils.parse(OtpPurpose.class, purposeStr, "purpose");
        boolean userExists = userRepository.existsByEmail(email.trim().toLowerCase());
        otpService.requestCode(email, purpose, userExists);
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        otpService.verifyCode(req.email(), req.code(), OtpPurpose.REGISTER);
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("EMAIL_EXISTS", "Email này đã được đăng ký.",
                    HttpStatus.BAD_REQUEST);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setName(req.name().trim());
        user.setRole(Role.LEARNER);
        user.setPlan(Plan.FREE);
        user.setEmailVerified(true);
        userRepository.save(user);
        return toAuthResponse(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("LOGIN_FAILED",
                        "Email hoặc mật khẩu không đúng.", HttpStatus.UNAUTHORIZED));

        if (user.getLockedUntil() != null && user.getLockedUntil().after(new Date())) {
            throw new ApiException("ACCOUNT_LOCKED",
                    "Tài khoản tạm khóa do nhập sai nhiều lần. Vui lòng thử lại sau 15 phút.",
                    HttpStatus.FORBIDDEN);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(new Date(System.currentTimeMillis() + LOCK_DURATION_MS));
                userRepository.save(user);
                throw new ApiException("ACCOUNT_LOCKED",
                        "Bạn đã nhập sai mật khẩu 5 lần. Tài khoản tạm khóa 15 phút.",
                        HttpStatus.FORBIDDEN);
            }
            user.setFailedLoginAttempts(attempts);
            userRepository.save(user);
            throw new ApiException("LOGIN_FAILED",
                    "Email hoặc mật khẩu không đúng.", HttpStatus.UNAUTHORIZED);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (planExpiryService.applyIfExpired(user)) {
            // goi PREMIUM het han -> ve FREE ngay luc login
        }
        userRepository.save(user);
        return toAuthResponse(user);
    }

    public void resetPassword(AuthDtos.PasswordResetRequest req) {
        otpService.verifyCode(req.email(), req.code(), OtpPurpose.RESET_PASSWORD);
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    public AuthDtos.MeResponse me(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));
        if (planExpiryService.applyIfExpired(user)) {
            userRepository.save(user);
        }
        return new AuthDtos.MeResponse(user.getId(), user.getEmail(), user.getName(),
                user.getRole().name(), user.getPlan().name(),
                user.getPlanExpiresAt(), user.getStreakDays());
    }

    private AuthDtos.AuthResponse toAuthResponse(User user) {
        String token = jwtUtil.generate(user.getId(), user.getEmail(), user.getRole().name());
        AuthDtos.UserDto dto = new AuthDtos.UserDto(user.getId(), user.getEmail(), user.getName(),
                user.getRole().name(), user.getPlan().name());
        return new AuthDtos.AuthResponse(token, dto);
    }
}
