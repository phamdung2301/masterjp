package com.masterjp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Date;

/**
 * Contract Auth (tuyet doi):
 * - Response LUON dung field "accessToken" (KHONG dung "token" — bug cu:
 *   FE destructure sai -> localStorage luu "undefined" -> Bearer undefined).
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record OtpRequest(
            @NotBlank(message = "Email không được để trống.")
            @Email(message = "Email không đúng định dạng.") String email,
            @NotNull(message = "purpose không được để trống.") String purpose // REGISTER | RESET_PASSWORD
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Email không được để trống.")
            @Email(message = "Email không đúng định dạng.") String email,
            @NotBlank(message = "Mã xác thực không được để trống.") String code,
            @NotBlank(message = "Mật khẩu không được để trống.")
            @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự.") String password,
            @NotBlank(message = "Tên hiển thị không được để trống.")
            @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự.") String name
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email không được để trống.")
            @Email(message = "Email không đúng định dạng.") String email,
            @NotBlank(message = "Mật khẩu không được để trống.") String password
    ) {
    }

    public record PasswordResetRequest(
            @NotBlank(message = "Email không được để trống.")
            @Email(message = "Email không đúng định dạng.") String email,
            @NotBlank(message = "Mã xác thực không được để trống.") String code,
            @NotBlank(message = "Mật khẩu mới không được để trống.")
            @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự.") String newPassword
    ) {
    }

    public record AuthResponse(String accessToken, UserDto user) {
    }

    public record UserDto(String id, String email, String name, String role, String plan) {
    }

    public record MeResponse(String id, String email, String name, String role, String plan,
                             Date planExpiresAt, int streakDays) {
    }
}
