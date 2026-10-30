package com.hms.auth;

import com.hms.config.AppProperties;
import com.hms.user.User;
import com.hms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Persists failed-attempt state independently of the rejected authentication transaction. */
@Service @RequiredArgsConstructor
public class LoginSecurityService {
    private final UserRepository users;
    private final LoginHistoryRepository history;
    private final AppProperties properties;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, String identity, String ip, String agent) {
        User user = userId == null ? null : users.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user != null && user.isActive()) {
            short failures = (short) (user.getFailedLoginAttempts() + 1);
            user.setFailedLoginAttempts(failures);
            if (failures >= properties.security().maxFailedAttempts()) user.setLockedUntil(Instant.now().plusSeconds(properties.security().lockoutMinutes() * 60L));
        }
        record(user, identity, LoginOutcome.FAILURE, ip, agent);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String identity, LoginOutcome outcome, String ip, String agent) {
        record(userId == null ? null : users.findByIdAndDeletedAtIsNull(userId).orElse(null), identity, outcome, ip, agent);
    }
    private void record(User user, String identity, LoginOutcome outcome, String ip, String agent) {
        LoginHistory row = new LoginHistory(); row.setUser(user); row.setUsernameAttempted(identity); row.setOutcome(outcome); row.setIpAddress(ip); row.setUserAgent(agent); row.setOccurredAt(Instant.now()); history.save(row);
    }
}
