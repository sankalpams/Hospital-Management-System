package lk.hms.common.audit;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.exception.BusinessException;
import lk.hms.modules.auth.entity.LoginAttempt;
import lk.hms.modules.auth.repository.LoginAttemptRepository;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

/** Read-only views of the audit trail and login activity (audit:read). */
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditLogRepository auditLogs;
    private final LoginAttemptRepository loginAttempts;

    public AuditController(AuditLogRepository auditLogs, LoginAttemptRepository loginAttempts) {
        this.auditLogs = auditLogs;
        this.loginAttempts = loginAttempts;
    }

    public record AuditLogResponse(Long id, Instant createdAt, String actorUsername, String action, String entityType,
                                   String entityId, String summary, String ipAddress, String requestId,
                                   String beforeJson, String afterJson) {
        static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getCreatedAt(), a.getActorUsername(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getSummary(), a.getIpAddress(), a.getRequestId(),
                    a.getBeforeJson(), a.getAfterJson());
        }
    }

    public record LoginAttemptResponse(Long id, Instant attemptedAt, String username, boolean success,
                                       String failureReason, String ipAddress, String userAgent) {
        static LoginAttemptResponse from(LoginAttempt a) {
            return new LoginAttemptResponse(a.getId(), a.getAttemptedAt(), a.getUsername(), a.isSuccess(),
                    a.getFailureReason(), a.getIpAddress(), a.getUserAgent());
        }
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('audit:read')")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Instant[] range = range(from, to);
        return PageResponse.of(auditLogs.search(like(q), blank(entityType), blank(action), range[0], range[1],
                Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"))), AuditLogResponse::from);
    }

    @GetMapping("/login-activity")
    @PreAuthorize("hasAuthority('audit:read')")
    @Transactional(readOnly = true)
    public PageResponse<LoginAttemptResponse> loginActivity(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Instant[] range = range(from, to);
        return PageResponse.of(loginAttempts.search(like(q), success, range[0], range[1],
                Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id"))), LoginAttemptResponse::from);
    }

    /** Date filters are inclusive calendar days (UTC boundaries; good enough for log browsing). */
    private static Instant[] range(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw BusinessException.badRequest("INVALID_RANGE", "'To' date must not be before 'from' date.");
        }
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z") : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new Instant[]{start, end};
    }

    private static String like(String q) {
        return q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
