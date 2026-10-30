package com.hms.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
public final class AuditDtos {
    private AuditDtos() {}
    public record AuditLogResponse(UUID id, UUID userId, String roleCode, AuditAction action, String entityType,
                                   String entityId, JsonNode beforeJson, JsonNode afterJson, String ipAddress, Instant occurredAt) {}
}
