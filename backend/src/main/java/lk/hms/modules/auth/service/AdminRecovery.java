package lk.hms.modules.auth.service;

import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.SessionRevoker;
import lk.hms.common.security.TemporaryPasswords;
import lk.hms.modules.users.entity.Role;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Last-resort recovery when no Super Admin can sign in (forgotten password, locked, deactivated).
 * Reachable only from the server console through {@link AdminRecoveryRunner}, never over HTTP: whoever
 * can run it already controls the server and its database credentials. Issues a temporary password
 * that must be changed at the next sign-in, clears the lock, ends existing sessions, and is audited.
 */
@Service
public class AdminRecovery {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevoker sessions;
    private final AuditService audit;

    public AdminRecovery(UserRepository users, PasswordEncoder passwordEncoder, SessionRevoker sessions, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.audit = audit;
    }

    /** @return the temporary password, to be shown once on the console */
    @Transactional
    public String recover(String rawUsername) {
        String username = User.normaliseUsername(rawUsername);
        User user = users.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_USER", "No account named '" + username + "'."));
        if (user.getRoles().stream().noneMatch(r -> Role.SUPER_ADMIN.equals(r.getCode()))) {
            throw BusinessException.badRequest("NOT_SUPER_ADMIN",
                    "'" + username + "' is not a Super Admin. Other accounts are reset by an administrator in the app.");
        }
        String temporary = TemporaryPasswords.generate();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        // same transaction (a separate one would wait on this user row); no signed-in actor: it came from the console
        audit.recordChange("ADMIN_RECOVERY", "USER", user.getId(),
                "Temporary password issued for " + username + " from the server console (break-glass recovery)", null, null);
        sessions.revokeAll(username);
        return temporary;
    }
}
