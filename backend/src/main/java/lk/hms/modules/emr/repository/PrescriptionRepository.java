package lk.hms.modules.emr.repository;

import lk.hms.modules.emr.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByEncounterIdInOrderByIdAsc(List<Long> encounterIds);

    @org.springframework.data.jpa.repository.Query("""
            select p from Prescription p where p.status in :statuses and (:patientId is null or p.patientId = :patientId)
            order by p.id asc
            """)
    List<Prescription> queue(java.util.Collection<Prescription.Status> statuses, Long patientId, org.springframework.data.domain.Pageable pageable);
}
