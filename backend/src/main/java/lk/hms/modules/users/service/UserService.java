package lk.hms.modules.users.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.common.security.SessionRevoker;
import lk.hms.common.security.TemporaryPasswords;
import lk.hms.modules.users.dto.UserDtos.CreateUserRequest;
import lk.hms.modules.users.dto.UserDtos.RoleResponse;
import lk.hms.modules.users.dto.UserDtos.SignUpRequest;
import lk.hms.modules.auth.service.PasswordPolicy;
import lk.hms.modules.users.dto.UserDtos.TemporaryPasswordResponse;
import lk.hms.modules.users.dto.UserDtos.UpdateUserRequest;
import lk.hms.modules.users.dto.UserDtos.UserResponse;
import lk.hms.modules.users.entity.Role;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.modules.users.repository.RoleRepository;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Account administration. Rules beyond CRUD:
 * <ul>
 *   <li>Only a Super Admin may grant SUPER_ADMIN or modify a Super Admin account (no self-escalation).</li>
 *   <li>Users cannot deactivate themselves; the last active Super Admin cannot be deactivated or demoted.</li>
 *   <li>Role changes, deactivation and password resets end the user's sessions (permissions live in the session).</li>
 *   <li>Temporary passwords are returned once and must be changed at next sign-in.</li>
 * </ul>
 */
@Service
public class UserService {

    private static final Map<String, String> SORTS = Map.of(
            "username", "username", "fullName", "fullName", "status", "status",
            "lastLoginAt", "lastLoginAt", "createdAt", "createdAt");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SessionRevoker sessionRevoker;
    private final Clock clock;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       AuditService audit, SessionRevoker sessionRevoker, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.sessionRevoker = sessionRevoker;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, String status, String role, int page, int size, String sort) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        UserStatus st = status == null || status.isBlank() ? null : parseStatus(status);
        String rl = role == null || role.isBlank() ? null : role;
        Instant now = Instant.now(clock);
        return PageResponse.of(users.search(pattern, st, rl,
                        Paging.of(page, size, sort, SORTS, Sort.by("fullName"))),
                u -> UserResponse.from(u, now));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(load(id), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roles.findAll(Sort.by("name")).stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public TemporaryPasswordResponse create(CreateUserRequest req) {
        String username = User.normaliseUsername(req.username());
        if (users.existsByUsername(username)) {
            throw BusinessException.conflict("USERNAME_TAKEN", "The username '" + username + "' is already in use.");
        }
        String email = blankToNull(req.email());
        if (email != null && users.existsByEmail(email)) {
            throw BusinessException.conflict("EMAIL_TAKEN", "Another account already uses this email address.");
        }
        Set<Role> assigned = resolveRoles(req.roles());
        String temporary = TemporaryPasswords.generate();

        User user = new User();
        user.setUsername(username);
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setRoles(assigned);
        users.saveAndFlush(user);

        audit.recordChange("CREATE", "USER", user.getId(), "Created account " + username, null, snapshot(user));
        return new TemporaryPasswordResponse(UserResponse.from(user, Instant.now(clock)), temporary);
    }

    /** Roles a visitor may ask for; administrator roles are only ever granted by an administrator. */
    static final Set<String> SIGN_UP_ROLES =
            Set.of("DOCTOR", "NURSE", "RECEPTIONIST", "LAB_STAFF", "PHARMACIST", "ACCOUNTANT", "HR_MANAGER");
    /** Stops a flood of requests from filling the table; an administrator clears the queue to reopen it. */
    static final long MAX_PENDING = 100;

    @Transactional
    public void signUp(SignUpRequest req) {
        String username = User.normaliseUsername(req.username());
        if (!SIGN_UP_ROLES.contains(req.role())) {
            throw BusinessException.badRequest("ROLE_NOT_REQUESTABLE", "Choose one of the listed staff roles.");
        }
        if (users.countByStatus(UserStatus.PENDING) >= MAX_PENDING) {
            throw BusinessException.conflict("SIGN_UP_QUEUE_FULL",
                    "Sign-up is paused because many requests are waiting. Try again later.");
        }
        if (users.existsByUsername(username)) {
            throw BusinessException.conflict("USERNAME_TAKEN", "The username '" + username + "' is already in use.");
        }
        String email = blankToNull(req.email());
        if (email != null && users.existsByEmail(email)) {
            throw BusinessException.conflict("EMAIL_TAKEN", "Another account already uses this email address.");
        }
        PasswordPolicy.validate(req.password(), username);
        Role role = roles.findByCode(req.role()).orElseThrow();

        User user = new User();
        user.setUsername(username);
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now(clock));
        user.setStatus(UserStatus.PENDING);
        user.setRoles(new HashSet<>(Set.of(role)));
        users.saveAndFlush(user);
        audit.recordChange("SIGN_UP", "USER", user.getId(), "Sign-up request from " + username, null, snapshot(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req) {
        User user = load(id);
        checkVersion(user, req.version());
        guardSuperAdminTarget(user);
        Map<String, Object> before = snapshot(user);

        String email = blankToNull(req.email());
        if (email != null && users.existsByEmailAndIdNot(email, id)) {
            throw BusinessException.conflict("EMAIL_TAKEN", "Another account already uses this email address.");
        }
        Set<Role> newRoles = resolveRoles(req.roles());
        boolean wasSuperAdmin = hasRole(user, Role.SUPER_ADMIN);
        boolean staysSuperAdmin = newRoles.stream().anyMatch(r -> r.getCode().equals(Role.SUPER_ADMIN));
        if (wasSuperAdmin && !staysSuperAdmin && user.getStatus() == UserStatus.ACTIVE
                && users.countActiveSuperAdmins() <= 1) {
            throw BusinessException.conflict("LAST_SUPER_ADMIN", "At least one active Super Admin must remain.");
        }
        Set<String> oldCodes = codes(user.getRoles());

        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setRoles(newRoles);
        users.saveAndFlush(user);

        audit.recordChange("UPDATE", "USER", id, "Updated account " + user.getUsername(), before, snapshot(user));
        if (!oldCodes.equals(codes(newRoles))) {
            revokeAfterCommit(user.getUsername());
        }
        return UserResponse.from(user, Instant.now(clock));
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active) {
        User user = load(id);
        guardSuperAdminTarget(user);
        HmsUserPrincipal me = CurrentUser.require();
        if (!active && Objects.equals(me.getId(), id)) {
            throw BusinessException.conflict("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account.");
        }
        if (!active && hasRole(user, Role.SUPER_ADMIN) && user.getStatus() == UserStatus.ACTIVE
                && users.countActiveSuperAdmins() <= 1) {
            throw BusinessException.conflict("LAST_SUPER_ADMIN", "At least one active Super Admin must remain.");
        }
        UserStatus target = active ? UserStatus.ACTIVE : UserStatus.INACTIVE;
        UserStatus previous = user.getStatus();
        if (previous == target) {
            return UserResponse.from(user, Instant.now(clock));
        }
        user.setStatus(target);
        if (active) {
            user.setLockedUntil(null);
            user.setFailedAttempts(0);
        }
        users.saveAndFlush(user);
        audit.recordChange("STATUS_CHANGE", "USER", id,
                (previous == UserStatus.PENDING ? (active ? "Approved sign-up of " : "Rejected sign-up of ")
                        : (active ? "Activated " : "Deactivated ")) + user.getUsername(),
                Map.of("status", previous.name()), Map.of("status", target.name()));
        if (!active) {
            revokeAfterCommit(user.getUsername());
        }
        return UserResponse.from(user, Instant.now(clock));
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(Long id) {
        User user = load(id);
        guardSuperAdminTarget(user);
        if (Objects.equals(CurrentUser.require().getId(), id)) {
            throw BusinessException.conflict("USE_CHANGE_PASSWORD", "Use 'Change password' for your own account.");
        }
        String temporary = TemporaryPasswords.generate();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        users.saveAndFlush(user);
        audit.recordChange("PASSWORD_RESET", "USER", id, "Temporary password issued for " + user.getUsername(), null, null);
        revokeAfterCommit(user.getUsername());
        return new TemporaryPasswordResponse(UserResponse.from(user, Instant.now(clock)), temporary);
    }

    // ---------------------------------------------------------------------

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> BusinessException.notFound("User", id));
    }

    private Set<Role> resolveRoles(Set<String> requested) {
        Set<Role> found = new HashSet<>();
        for (String code : requested) {
            Role role = roles.findByCode(code)
                    .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_ROLE", "Unknown role: " + code));
            if (role.getCode().equals(Role.SUPER_ADMIN) && !callerIsSuperAdmin()) {
                throw BusinessException.forbidden("SUPER_ADMIN_REQUIRED", "Only a Super Admin can assign the Super Admin role.");
            }
            found.add(role);
        }
        return found;
    }

    private void guardSuperAdminTarget(User target) {
        if (hasRole(target, Role.SUPER_ADMIN) && !callerIsSuperAdmin()) {
            throw BusinessException.forbidden("SUPER_ADMIN_REQUIRED", "Only a Super Admin can modify a Super Admin account.");
        }
    }

    private static boolean callerIsSuperAdmin() {
        return CurrentUser.get().map(p -> p.getRoles().contains(Role.SUPER_ADMIN)).orElse(false);
    }

    private static void checkVersion(User user, Long expected) {
        if (expected != null && expected != user.getVersion()) {
            throw BusinessException.conflict("CONCURRENT_MODIFICATION",
                    "This account was changed by someone else. Reload it and try again.");
        }
    }

    private static boolean hasRole(User u, String code) {
        return u.getRoles().stream().anyMatch(r -> r.getCode().equals(code));
    }

    private static Set<String> codes(Set<Role> roles) {
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }

    private static Map<String, Object> snapshot(User u) {
        return Map.of("username", u.getUsername(), "fullName", u.getFullName(),
                "email", Objects.toString(u.getEmail(), ""), "status", u.getStatus().name(),
                "roles", codes(u.getRoles()).stream().sorted().toList());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private static UserStatus parseStatus(String s) {
        try {
            return UserStatus.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown status: " + s);
        }
    }

    /** Sessions are only ended once the change is committed (a rollback must not log people out). */
    private void revokeAfterCommit(String username) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionRevoker.revokeAll(username);
            }
        });
    }
}
