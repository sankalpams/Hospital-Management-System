package com.hms.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "audit_logs") @Getter @Setter @NoArgsConstructor
public class AuditLog extends BaseEntity {
    @Id @GeneratedValue private UUID id;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "role_code") private String roleCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AuditAction action;
    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id") private String entityId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_json", columnDefinition = "jsonb") private JsonNode beforeJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_json", columnDefinition = "jsonb") private JsonNode afterJson;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
}
