package lk.hms.modules.auth.service;

import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.common.settings.SettingsService;
import lk.hms.modules.auth.entity.LoginAttempt;
import lk.hms.modules.auth.repository.LoginAttemptRepository;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.modules.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Authentication rules (docs/01 §2A):
 * <ul>
 *   <li>Unknown usernames still run a BCrypt comparison so response time does not reveal which accounts exist.</li>
 *   <li>Account status is only revealed <em>after</em> the password is verified.</li>
 *   <li>{@code security.max_failed_logins} bad passwords within {@code security.lock_minutes}
 *       lock the account for {@code security.lock_minutes}.</li>
 *   <li>Every attempt is written to login_attempts in its own transaction, so it survives the failed login.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final LoginAttemptRepository attempts;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, LoginAttemptRepository attempts, PasswordEncoder passwordEncoder,
                       AuditService audit, SettingsService settings, PlatformTransactionManager txManager,
                       Clock clock) {
        this.users = users;
        this.attempts = attempts;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.settings = settings;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password-1");
    }

    public record ClientInfo(String ipAddress, String userAgent) {
    }

    public HmsUserPrincipal login(String rawUsername, String rawPassword, ClientInfo client) {
        String username = User.normaliseUsername(rawUsername);
        Instant now = Instant.now(clock);
        throttleIp(username, client, now);

        Optional<User> found = tx.execute(s -> users.findWithAuthoritiesByUsername(username));
        if (found == null || found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            recordAttempt(username, null, false, "BAD_CREDENTIALS", client, now);
            throw badCredentials();
        }
        User user = found.get();

        if (user.isLockedAt(now)) {
            recordAttempt(username, user.getId(), false, "LOCKED", client, now);
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            throw new BusinessException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed sign-in attempts. Try again in about " + minutes + " minute(s).");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            handleBadPassword(user.getId(), username, client, now);
            throw badCredentials();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            recordAttempt(username, user.getId(), false, "INACTIVE", client, now);
            throw BusinessException.forbidden("ACCOUNT_INACTIVE",
                    "This account is deactivated. Contact your administrator.");
        }

        return tx.execute(s -> {
            User fresh = users.findWithAuthoritiesByUsername(username).orElseThrow();
            fresh.setFailedAttempts(0);
            fresh.setLockedUntil(null);
            fresh.setLastLoginAt(now);
            attempts.save(attempt(username, fresh.getId(), true, null, client, now));
            audit.recordSecurityEvent("LOGIN", fresh.getId(), username, "Signed in");
            return HmsUserPrincipal.from(fresh);
        });
    }

    /** Pauses an address that has failed too often, before any password is checked (no hashing work for an attacker). */
    private void throttleIp(String username, ClientInfo client, Instant now) {
        if (client == null || client.ipAddress() == null) {
            return;
        }
        int max = settings.getInt("security.ip_max_failed_logins", 30);
        int window = settings.getInt("security.ip_window_minutes", 15);
        long failures = attempts.countRecentFailuresFromIp(client.ipAddress(), now.minus(Duration.ofMinutes(window)));
        if (failures >= max) {
            recordAttempt(username, null, false, "IP_THROTTLED", client, now);
            log.warn("Sign-in paused for {} after {} failures in {} min", client.ipAddress(), failures, window);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-ins from this computer. Try again in " + window + " minutes.");
        }
    }

    private void handleBadPassword(Long userId, String username, ClientInfo client, Instant now) {
        int maxFailures = settings.getInt("security.max_failed_logins", 5);
        int lockMinutes = settings.getInt("security.lock_minutes", 15);
        tx.executeWithoutResult(s -> {
            User user = users.findById(userId).orElseThrow();
            attempts.save(attempt(username, userId, false, "BAD_CREDENTIALS", client, now));

            // Count failures in the window, but never before the last success or the end of the previous lock.
            Instant since = now.minus(Duration.ofMinutes(lockMinutes));
            if (user.getLastLoginAt() != null && user.getLastLoginAt().isAfter(since)) {
                since = user.getLastLoginAt();
            }
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(since)) {
                since = user.getLockedUntil();
            }
            int failures = (int) attempts.countRecentFailures(userId, since.minusMillis(1));
            user.setFailedAttempts(failures);
            if (failures >= maxFailures) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(lockMinutes)));
                user.setFailedAttempts(0);
                audit.recordSecurityEvent("ACCOUNT_LOCKED", userId, username,
                        "Locked for " + lockMinutes + " min after " + failures + " failed sign-ins");
                log.warn("Account {} locked after {} failed sign-ins", username, failures);
            }
        });
    }

    private void recordAttempt(String username, Long userId, boolean success, String reason,
                               ClientInfo client, Instant now) {
        tx.executeWithoutResult(s -> attempts.save(attempt(username, userId, success, reason, client, now)));
    }

    private static LoginAttempt attempt(String username, Long userId, boolean success, String reason,
                                        ClientInfo client, Instant now) {
        LoginAttempt a = new LoginAttempt();
        a.setUsername(username == null ? "" : username.substring(0, Math.min(50, username.length())));
        a.setUserId(userId);
        a.setSuccess(success);
        a.setFailureReason(reason);
        a.setIpAddress(client.ipAddress());
        a.setUserAgent(client.userAgent() == null ? null
                : client.userAgent().substring(0, Math.min(255, client.userAgent().length())));
        a.setAttemptedAt(now);
        return a;
    }

    private static BusinessException badCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Incorrect username or password.");
    }

    /** Changes the caller's password and returns the refreshed principal (mustChangePassword cleared). */
    public HmsUserPrincipal changePassword(Long userId, String currentPassword, String newPassword) {
        return tx.execute(s -> {
            User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("User", userId));
            if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw BusinessException.badRequest("CURRENT_PASSWORD_INCORRECT", "Your current password is incorrect.");
            }
            PasswordPolicy.validate(newPassword, user.getUsername());
            if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                throw BusinessException.badRequest("PASSWORD_REUSED", "Choose a password different from your current one.");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.setPasswordChangedAt(Instant.now(clock));
            audit.recordChange("PASSWORD_CHANGED", "USER", user.getId(), "Password changed by owner", null, null);
            return HmsUserPrincipal.from(users.findWithAuthoritiesByUsername(user.getUsername()).orElseThrow());
        });
    }
}
