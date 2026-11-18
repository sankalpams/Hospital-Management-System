package lk.hms.modules.lab.repository;

import lk.hms.modules.lab.entity.LabRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LabRequestRepository extends JpaRepository<LabRequest, Long> {

    /** Requests with at least one item in the given statuses (the lab worklist). */
    @Query("""
            select r from LabRequest r
            where exists (select 1 from LabRequestItem i where i.request = r and i.status in :statuses)
              and (:patientId is null or r.patientId = :patientId)
              and (:priority is null or r.priority = :priority)
              and (:q is null or r.requestNo = :q
                   or r.patientId in (select p.id from Patient p where p.mrn = :q or lower(concat(p.firstName, ' ', p.lastName)) like :like))
              and r.requestedAt >= :from and r.requestedAt < :to
            """)
    Page<LabRequest> search(Collection<lk.hms.modules.lab.entity.LabRequestItem.Status> statuses, Long patientId, String priority,
                            String q, String like, Instant from, Instant to, Pageable pageable);

    List<LabRequest> findByEncounterIdOrderByIdAsc(Long encounterId);
}
