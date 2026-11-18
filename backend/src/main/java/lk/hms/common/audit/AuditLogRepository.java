package lk.hms.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, String entityId);

    @Query("""
            select a from AuditLog a
            where (:q is null or lower(a.actorUsername) like :q or lower(a.summary) like :q or a.entityId like :q)
              and (:entityType is null or a.entityType = :entityType)
              and (:action is null or a.action = :action)
              and a.createdAt >= :from and a.createdAt < :to
            """)
    Page<AuditLog> search(String q, String entityType, String action, Instant from, Instant to, Pageable pageable);
}
