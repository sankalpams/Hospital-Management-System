package com.hms.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Query("select a from AuditLog a where (:q is null or lower(a.entityType) like lower(concat('%', :q, '%')) or lower(coalesce(a.entityId, '')) like lower(concat('%', :q, '%'))) and " +
        "(:action is null or a.action = :action) and (:from is null or a.occurredAt >= :from) and (:to is null or a.occurredAt <= :to)")
    Page<AuditLog> search(String q, AuditAction action, Instant from, Instant to, Pageable pageable);
}
