package com.hms.auth;

import com.hms.audit.AuditService;
import com.hms.common.AppException;
import com.hms.config.AppProperties;
import com.hms.role.Permission;
import com.hms.role.PermissionCode;
import com.hms.role.Role;
import com.hms.role.RoleCode;
import com.hms.security.JwtService;
import com.hms.security.LoginRateLimiter;
import com.hms.user.User;
import com.hms.user.UserDtos;
import com.hms.user.UserMapper;
import com.hms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private TokenHasher tokens;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordResetTokenRepository resetTokens;
    @Mock private LoginHistoryRepository loginHistory;
    @Mock private LoginSecurityService loginSecurity;
    @Mock private LoginRateLimiter rateLimiter;
    @Mock private PasswordPolicy passwordPolicy;
    @Mock private PasswordMailService mail;
    @Mock private AuditService audits;
    @Mock private HttpServletRequest servletRequest;

    private AppProperties properties;
    private AuthService authService;

    private User testUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                new AppProperties.Cors("http://localhost:8080"),
                new AppProperties.Security("ZGV2LXNlY3JldC1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc=", 15, 7, 15, 5),
                new AppProperties.Mail("no-reply@hms.local", "http://localhost:8080/reset-password"),
                new AppProperties.Storage("./target/test-uploads")
        );

        authService = new AuthService(
                users, userMapper, passwordEncoder, jwtService, tokens,
                refreshTokens, resetTokens, loginHistory, loginSecurity,
                rateLimiter, passwordPolicy, mail, audits, properties
        );

        adminRole = new Role(RoleCode.ADMIN, "Admin", "Administrator role");
        adminRole.getPermissions().add(new Permission(PermissionCode.USER_READ, "User read"));

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("admin");
        testUser.setEmail("admin@hms.local");
        testUser.setPasswordHash("$2a$12$hashedPassword");
        testUser.setFirstName("Amara");
        testUser.setLastName("Perera");
        testUser.setRole(adminRole);
        testUser.setActive(true);
        testUser.setForcePasswordChange(false);
        testUser.setFailedLoginAttempts((short) 0);
    }

    @Test
    @DisplayName("Successful login returns access token and refresh token")
    void login_Success() {
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        when(users.findActiveLoginUser("admin")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Welcome@12345", "$2a$12$hashedPassword")).thenReturn(true);
        when(jwtService.createAccessToken(eq(testUser.getId()), eq("admin"), eq("ADMIN"), any())).thenReturn("access.jwt.token");
        when(tokens.newToken()).thenReturn("raw-refresh-token");
        when(tokens.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");

        UserDtos.UserResponse userResponse = new UserDtos.UserResponse(
                testUser.getId(), testUser.getUsername(), testUser.getEmail(),
                testUser.getFirstName(), testUser.getLastName(), testUser.getPhone(),
                RoleCode.ADMIN, true, false, null, Instant.now(), 0L
        );
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("admin", "Welcome@12345");
        AuthDtos.TokenResponse response = authService.login(request, servletRequest);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(testUser.getFailedLoginAttempts()).isEqualTo((short) 0);
        assertThat(testUser.getLockedUntil()).isNull();
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Login with invalid password records failure and throws BadCredentialsException")
    void login_InvalidPassword_ThrowsBadCredentials() {
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        when(users.findActiveLoginUser("admin")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("admin", "WrongPassword");

        assertThatThrownBy(() -> authService.login(request, servletRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(loginSecurity).recordFailure(eq(testUser.getId()), eq("admin"), eq("127.0.0.1"), any());
    }

    @Test
    @DisplayName("Login when account is locked throws ACCOUNT_LOCKED AppException")
    void login_LockedAccount_ThrowsAppException() {
        testUser.setLockedUntil(Instant.now().plusSeconds(900));

        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        when(users.findActiveLoginUser("admin")).thenReturn(Optional.of(testUser));

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("admin", "Welcome@12345");

        assertThatThrownBy(() -> authService.login(request, servletRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("ACCOUNT_LOCKED");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @DisplayName("Login when rate limited throws RATE_LIMITED AppException")
    void login_RateLimited_ThrowsAppException() {
        when(servletRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(rateLimiter.allow("192.168.1.100")).thenReturn(false);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("admin", "Welcome@12345");

        assertThatThrownBy(() -> authService.login(request, servletRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });
    }

    @Test
    @DisplayName("Rotating refresh token revokes old token and issues fresh pair")
    void refresh_RotatesTokenSuccessfully() {
        RefreshToken existingToken = new RefreshToken();
        existingToken.setId(UUID.randomUUID());
        existingToken.setUser(testUser);
        existingToken.setTokenHash("old-hash");
        existingToken.setExpiresAt(Instant.now().plusSeconds(86400));

        when(tokens.hash("valid-old-refresh-token")).thenReturn("old-hash");
        when(refreshTokens.findByTokenHash("old-hash")).thenReturn(Optional.of(existingToken));
        when(jwtService.createAccessToken(any(), any(), any(), any())).thenReturn("new-jwt-token");
        when(tokens.newToken()).thenReturn("new-raw-refresh-token");
        when(tokens.hash("new-raw-refresh-token")).thenReturn("new-hash");

        RefreshToken freshToken = new RefreshToken();
        freshToken.setId(UUID.randomUUID());
        freshToken.setUser(testUser);
        freshToken.setTokenHash("new-hash");
        when(refreshTokens.findByTokenHash("new-hash")).thenReturn(Optional.of(freshToken));

        AuthDtos.TokenResponse response = authService.refresh(new AuthDtos.RefreshRequest("valid-old-refresh-token"));

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-jwt-token");
        assertThat(response.refreshToken()).isEqualTo("new-raw-refresh-token");
        assertThat(existingToken.getRevokedAt()).isNotNull();
        assertThat(existingToken.getReplacedById()).isEqualTo(freshToken.getId());
    }

    @Test
    @DisplayName("Change password verifies current password, validates policy, updates hash and revokes existing tokens")
    void changePassword_Success() {
        UUID userId = testUser.getId();
        when(users.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("OldPassword@123", testUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPassword@2026")).thenReturn("$2a$12$newHashedPassword");

        AuthDtos.ChangePasswordRequest request = new AuthDtos.ChangePasswordRequest("OldPassword@123", "NewPassword@2026");
        authService.changePassword(userId, request);

        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newHashedPassword");
        assertThat(testUser.isForcePasswordChange()).isFalse();
        verify(passwordPolicy).validate("NewPassword@2026");
        verify(refreshTokens).revokeAllForUser(eq(userId), any(Instant.class));
    }

    @Test
    @DisplayName("Forgot password issues reset token and sends email via mail service")
    void forgotPassword_SendsEmail() {
        when(users.findActiveLoginUser("admin@hms.local")).thenReturn(Optional.of(testUser));
        when(tokens.newToken()).thenReturn("reset-token-raw");
        when(tokens.hash("reset-token-raw")).thenReturn("reset-token-hash");

        authService.forgotPassword(new AuthDtos.ForgotPasswordRequest("admin@hms.local"));

        verify(resetTokens).save(any(PasswordResetToken.class));
        verify(mail).sendReset(eq(testUser), eq("reset-token-raw"));
    }

    @Test
    @DisplayName("Reset password updates user password, marks token used, and clears lockouts")
    void resetPassword_Success() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setId(UUID.randomUUID());
        resetToken.setUser(testUser);
        resetToken.setTokenHash("reset-token-hash");
        resetToken.setExpiresAt(Instant.now().plusSeconds(1800));

        when(tokens.hash("reset-token-raw")).thenReturn("reset-token-hash");
        when(resetTokens.findByTokenHash("reset-token-hash")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewStrongPassword@2026")).thenReturn("$2a$12$newEncoded");

        authService.resetPassword(new AuthDtos.ResetPasswordRequest("reset-token-raw", "NewStrongPassword@2026"));

        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newEncoded");
        assertThat(testUser.isForcePasswordChange()).isFalse();
        assertThat(testUser.getFailedLoginAttempts()).isEqualTo((short) 0);
        assertThat(testUser.getLockedUntil()).isNull();
        assertThat(resetToken.getUsedAt()).isNotNull();
        verify(refreshTokens).revokeAllForUser(eq(testUser.getId()), any(Instant.class));
    }
}

