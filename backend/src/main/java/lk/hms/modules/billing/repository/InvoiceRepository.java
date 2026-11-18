package lk.hms.modules.billing.repository;

import jakarta.persistence.LockModeType;
import lk.hms.modules.billing.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Serialises payments/refunds/voids on one invoice so balances can never go wrong under concurrency. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> lockById(Long id);

    @Query("""
            select i from Invoice i
            where i.status in :statuses
              and (:patientId is null or i.patientId = :patientId)
              and (:q is null or i.invoiceNo = :q
                   or i.patientId in (select p.id from Patient p where p.mrn = :q or lower(concat(p.firstName, ' ', p.lastName)) like :like))
              and i.createdAt >= :from and i.createdAt < :to
            """)
    Page<Invoice> search(Collection<Invoice.Status> statuses, Long patientId, String q, String like, Instant from, Instant to, Pageable pageable);
}
