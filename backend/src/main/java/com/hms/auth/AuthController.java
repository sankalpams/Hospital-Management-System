package com.hms.auth;

import com.hms.security.CurrentUser;
import com.hms.user.UserDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    @PostMapping("/login") public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest servletRequest) { return auth.login(request, servletRequest); }
    @PostMapping("/refresh") public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) { return auth.refresh(request); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@Valid @RequestBody AuthDtos.LogoutRequest request, HttpServletRequest servletRequest) { auth.logout(request, servletRequest); }
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void forgot(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) { auth.forgotPassword(request); }
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) { auth.resetPassword(request); }
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void change(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) { auth.changePassword(me(), request); }
    @GetMapping("/me") public UserDtos.UserResponse profile() { return auth.profile(me()); }
    private UUID me() { return CurrentUser.id().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("No user")); }
}
