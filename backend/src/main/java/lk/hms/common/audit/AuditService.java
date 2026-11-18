package lk.hms.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.common.web.RequestIdFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;

/**
 * Writes audit rows inside the caller's transaction (Propagation.MANDATORY for changes), so
 * an audited change and its audit entry commit or roll back together.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Record a data change. Must be called from within a service transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChange(String action, String entityType, Object entityId, String summary,
                             Object before, Object after) {
        save(buildEntry(action, entityType, entityId, summary, before, after, CurrentUser.get().orElse(null)));
    }

    /**
     * Record a security event (login, logout, failed login) that has no surrounding business
     * transaction. Runs in its own transaction so it persists even if the caller fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(String action, Long actorUserId, String actorUsername, String summary) {
        AuditLog entry = buildEntry(action, "USER", actorUserId, summary, null, null, null);
        entry.setActorUserId(actorUserId);
        entry.setActorUsername(actorUsername);
        save(entry);
    }

    private AuditLog buildEntry(String action, String entityType, Object entityId, String summary,
                                Object before, Object after, HmsUserPrincipal actor) {
        AuditLog log = new AuditLog();
        if (actor != null) {
            log.setActorUserId(actor.getId());
            log.setActorUsername(actor.getUsername());
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setSummary(summary == null ? null : truncate(summary, 255));
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setIpAddress(clientIp());
        log.setRequestId(RequestIdFilter.current());
        log.setCreatedAt(Instant.now(clock));
        return log;
    }

    private void save(AuditLog log) {
        repository.save(log);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit snapshot is not serialisable", e);
        }
    }

    /** With server.forward-headers-strategy=native, getRemoteAddr() already reflects X-Forwarded-For from Apache. */
    static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return truncate(request.getRemoteAddr(), 45);
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
