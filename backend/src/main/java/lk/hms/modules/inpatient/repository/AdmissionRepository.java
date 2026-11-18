package lk.hms.modules.inpatient.repository;

import jakarta.persistence.LockModeType;
import lk.hms.modules.inpatient.entity.Admission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admission a where a.id = :id")
    Optional<Admission> lockById(Long id);

    boolean existsByPatientIdAndStatusIn(Long patientId, Collection<Admission.Status> statuses);

    @Query("""
            select a from Admission a
            where a.status in :statuses
              and (:patientId is null or a.patientId = :patientId)
              and (:q is null or a.admissionNo = :q
                   or a.patientId in (select p.id from Patient p where p.mrn = :q or lower(concat(p.firstName, ' ', p.lastName)) like :like))
            """)
    Page<Admission> search(Collection<Admission.Status> statuses, Long patientId, String q, String like, Pageable pageable);
}
