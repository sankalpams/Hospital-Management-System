package com.hms.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;
    private final HttpServletRequest request;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String entityType, String entityId, JsonNode before, JsonNode after) {
        recordFor(CurrentUser.id().orElse(null), CurrentUser.role().orElse(null), action, entityType, entityId, before, after);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFor(java.util.UUID userId, String roleCode, AuditAction action, String entityType, String entityId, JsonNode before, JsonNode after) {
        AuditLog log = new AuditLog();
        log.setUserId(userId); log.setRoleCode(roleCode);
        log.setAction(action); log.setEntityType(entityType); log.setEntityId(entityId);
        log.setBeforeJson(before); log.setAfterJson(after); log.setOccurredAt(Instant.now()); log.setIpAddress(clientIp());
        repository.save(log);
    }
    private String clientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
