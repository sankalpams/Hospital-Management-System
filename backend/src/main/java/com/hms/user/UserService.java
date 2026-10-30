package com.hms.user;

import com.hms.audit.AuditAction;
import com.hms.audit.Audited;
import com.hms.auth.AuthService;
import com.hms.auth.PasswordPolicy;
import com.hms.common.AppException;
import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import com.hms.role.Role;
import com.hms.role.RoleCode;
import com.hms.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final AuthService authService;
    private final PasswordPolicy passwordPolicy;
    @Transactional(readOnly = true)
    public PageResponse<UserDtos.UserResponse> list(String q, RoleCode role, Boolean active, int page, int size, String sort) {
        Page<UserDtos.UserResponse> usersPage = users.search(blank(q), role, active, PageableFactory.of(page, size, sort)).map(mapper::toResponse);
        return PageResponse.from(usersPage);
    }
    @Transactional(readOnly = true)
    public UserDtos.UserResponse get(UUID id) { return mapper.toResponse(required(id)); }
    @Audited(action = AuditAction.CREATE, entityType = "User")
    @Transactional
    public UserDtos.UserResponse create(UserDtos.CreateUserRequest request) {
        if (users.existsByUsernameIgnoreCase(request.username())) throw AppException.conflict("That username is already in use.");
        if (users.existsByEmailIgnoreCase(request.email())) throw AppException.conflict("That email address is already in use.");
        Role role = role(request.role());
        String temporary = request.temporaryPassword() == null || request.temporaryPassword().isBlank() ? "Welcome@12345" : request.temporaryPassword();
        passwordPolicy.validate(temporary);
        User user = new User(); user.setUsername(request.username().trim()); user.setEmail(request.email().trim().toLowerCase());
        user.setFirstName(request.firstName().trim()); user.setLastName(request.lastName().trim()); user.setPhone(blank(request.phone()));
        user.setRole(role); user.setPasswordHash(passwordEncoder.encode(temporary)); user.setForcePasswordChange(true); user.setActive(true);
        return mapper.toResponse(users.save(user));
    }
    @Audited(action = AuditAction.UPDATE, entityType = "User", entityIdArgument = 0)
    @Transactional
    public UserDtos.UserResponse update(UUID id, UserDtos.UpdateUserRequest request) {
        User user = required(id); String email = request.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) throw AppException.conflict("That email address is already in use.");
        user.setEmail(email); user.setFirstName(request.firstName().trim()); user.setLastName(request.lastName().trim()); user.setPhone(blank(request.phone()));
        if (request.role() != null) user.setRole(role(request.role())); if (request.active() != null) user.setActive(request.active());
        return mapper.toResponse(user);
    }
    @Audited(action = AuditAction.DELETE, entityType = "User", entityIdArgument = 0)
    @Transactional
    public UserDtos.UserResponse deactivate(UUID id, UUID actorId) {
        if (id.equals(actorId)) throw AppException.business("You cannot deactivate your own account.");
        User user = required(id); user.setActive(false); return mapper.toResponse(user);
    }
    @Audited(action = AuditAction.DELETE, entityType = "User", entityIdArgument = 0)
    @Transactional
    public void delete(UUID id, UUID actorId) {
        if (id.equals(actorId)) throw AppException.business("You cannot delete your own account.");
        User user = required(id); user.setActive(false); user.setDeletedAt(Instant.now());
    }
    @Transactional
    public UserDtos.UserResponse updateProfile(UUID id, UserDtos.UpdateUserRequest request) {
        User user = required(id); String email = request.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) throw AppException.conflict("That email address is already in use.");
        user.setEmail(email); user.setFirstName(request.firstName().trim()); user.setLastName(request.lastName().trim()); user.setPhone(blank(request.phone()));
        return mapper.toResponse(user);
    }
    @Transactional public void sendAdminReset(UUID id) { authService.sendResetForUser(id); }
    private User required(UUID id) { return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> AppException.notFound("User not found.")); }
    private Role role(RoleCode code) { return roles.findWithPermissionsByCode(code == null ? RoleCode.RECEPTIONIST : code).orElseThrow(() -> AppException.business("The selected role is unavailable.")); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
