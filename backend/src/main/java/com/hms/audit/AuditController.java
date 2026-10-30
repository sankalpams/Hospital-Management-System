package com.hms.audit;

import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController @RequestMapping("/api/v1/audit-logs") @RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUDIT_READ')")
public class AuditController {
    private final AuditLogRepository logs;
    @GetMapping
    public PageResponse<AuditDtos.AuditLogResponse> list(@RequestParam(required = false) String q,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "occurredAt,desc") String sort) {
        Page<AuditDtos.AuditLogResponse> result = logs.search(blankToNull(q), action, from, to, PageableFactory.of(page, size, sort)).map(a ->
            new AuditDtos.AuditLogResponse(a.getId(), a.getUserId(), a.getRoleCode(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getBeforeJson(), a.getAfterJson(), a.getIpAddress(), a.getOccurredAt()));
        return PageResponse.from(result);
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
