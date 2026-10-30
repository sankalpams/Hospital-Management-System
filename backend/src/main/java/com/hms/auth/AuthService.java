package com.hms.auth;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hms.audit.AuditAction;
import com.hms.audit.AuditService;
import com.hms.common.AppException;
import com.hms.config.AppProperties;
import com.hms.security.JwtService;
import com.hms.security.LoginRateLimiter;
import com.hms.user.User;
import com.hms.user.UserDtos;
import com.hms.user.UserMapper;
import com.hms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHasher tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final LoginHistoryRepository loginHistory;
    private final LoginSecurityService loginSecurity;
    private final LoginRateLimiter rateLimiter;
    private final PasswordPolicy passwordPolicy;
    private final PasswordMailService mail;
    private final AuditService audits;
    private final AppProperties properties;

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request, HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        if (!rateLimiter.allow(ip)) {
            loginSecurity.record(null, request.identity(), LoginOutcome.RATE_LIMITED, ip, servletRequest.getHeader("User-Agent"));
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many sign-in attempts. Please try again in a minute.");
        }
        User user = users.findActiveLoginUser(request.identity()).orElse(null);
        Instant now = Instant.now();
        if (user == null || !user.isActive()) {
            loginSecurity.recordFailure(user == null ? null : user.getId(), request.identity(), ip, servletRequest.getHeader("User-Agent"));
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        if (user.isLocked(now)) {
            loginSecurity.record(user.getId(), request.identity(), LoginOutcome.LOCKED, ip, servletRequest.getHeader("User-Agent"));
            throw new AppException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", "This account is temporarily locked. Try again later or contact an administrator.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginSecurity.recordFailure(user.getId(), request.identity(), ip, servletRequest.getHeader("User-Agent"));
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        user.setFailedLoginAttempts((short) 0); user.setLockedUntil(null); user.setLastLoginAt(now);
        recordHistory(user, request.identity(), LoginOutcome.SUCCESS, ip, servletRequest.getHeader("User-Agent"));
        audits.recordFor(user.getId(), user.getRole().getCode().name(), AuditAction.LOGIN, "AuthSession", user.getId().toString(), null, event("outcome", "SUCCESS"));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshToken token = refreshTokens.findByTokenHash(tokens.hash(request.refreshToken())).orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again."));
        Instant now = Instant.now();
        if (!token.isUsable(now) || !token.getUser().isActive()) throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again.");
        token.setRevokedAt(now);
        AuthDtos.TokenResponse response = issueTokens(token.getUser());
        String freshHash = tokens.hash(response.refreshToken());
        refreshTokens.findByTokenHash(freshHash).ifPresent(replacement -> token.setReplacedById(replacement.getId()));
        return response;
    }

    @Transactional
    public void logout(AuthDtos.LogoutRequest request, HttpServletRequest servletRequest) {
        refreshTokens.findByTokenHash(tokens.hash(request.refreshToken())).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            recordHistory(token.getUser(), token.getUser().getUsername(), LoginOutcome.LOGOUT, clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        });
    }

    @Transactional
    public void changePassword(UUID userId, AuthDtos.ChangePasswordRequest request) {
        User user = requiredUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) throw new AppException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Your current password is incorrect.");
        passwordPolicy.validate(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword())); user.setForcePasswordChange(false);
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional
    public void forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        users.findActiveLoginUser(request.email()).filter(User::isActive).ifPresent(this::sendReset);
    }

    @Transactional
    public void sendResetForUser(UUID id) { sendReset(requiredUser(id)); }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordResetToken token = resetTokens.findByTokenHash(tokens.hash(request.token())).orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "This password reset link is invalid or has expired."));
        if (!token.isUsable(Instant.now())) throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "This password reset link is invalid or has expired.");
        passwordPolicy.validate(request.newPassword());
        User user = token.getUser(); user.setPasswordHash(passwordEncoder.encode(request.newPassword())); user.setForcePasswordChange(false); user.setFailedLoginAttempts((short) 0); user.setLockedUntil(null);
        token.setUsedAt(Instant.now()); refreshTokens.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse profile(UUID id) { return userMapper.toResponse(requiredUser(id)); }

    private AuthDtos.TokenResponse issueTokens(User user) {
        Set<String> permissions = user.getRole().getPermissions().stream().map(p -> p.getCode().name()).collect(java.util.stream.Collectors.toSet());
        String access = jwtService.createAccessToken(user.getId(), user.getUsername(), user.getRole().getCode().name(), permissions);
        String raw = tokens.newToken(); RefreshToken refresh = new RefreshToken(); refresh.setUser(user); refresh.setTokenHash(tokens.hash(raw));
        refresh.setExpiresAt(Instant.now().plusSeconds(properties.security().refreshTokenDays() * 86400L)); refreshTokens.save(refresh);
        return new AuthDtos.TokenResponse(access, raw, user.isForcePasswordChange(), userMapper.toResponse(user));
    }
    private User requiredUser(UUID id) { return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> AppException.notFound("User not found.")); }
    private void sendReset(User user) {
        String raw = tokens.newToken(); PasswordResetToken token = new PasswordResetToken();
        token.setUser(user); token.setTokenHash(tokens.hash(raw)); token.setExpiresAt(Instant.now().plusSeconds(1800)); resetTokens.save(token);
        mail.sendReset(user, raw);
    }
    private void recordHistory(User user, String identity, LoginOutcome outcome, String ip, String agent) {
        LoginHistory row = new LoginHistory(); row.setUser(user); row.setUsernameAttempted(identity); row.setOutcome(outcome); row.setIpAddress(ip); row.setUserAgent(agent); row.setOccurredAt(Instant.now()); loginHistory.save(row);
    }
    private String clientIp(HttpServletRequest request) { String f = request.getHeader("X-Forwarded-For"); return f == null || f.isBlank() ? request.getRemoteAddr() : f.split(",")[0].trim(); }
    private ObjectNode event(String key, String value) { ObjectNode node = JsonNodeFactory.instance.objectNode(); node.put(key, value); return node; }
}
