package com.hms.auth;

import com.hms.user.UserDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank String identity, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String refreshToken, boolean forcePasswordChange, UserDtos.UserResponse user) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(@NotBlank String refreshToken) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}
    public record LoginHistoryResponse(UUID id, String usernameAttempted, LoginOutcome outcome, String ipAddress, Instant occurredAt) {}
}
