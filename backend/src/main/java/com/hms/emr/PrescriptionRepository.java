package com.hms.emr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Optional<Prescription> findByPrescriptionNumber(String prescriptionNumber);

    List<Prescription> findByEncounterIdAndDeletedFalseOrderByCreatedAtDesc(UUID encounterId);

    Page<Prescription> findByPatientIdAndDeletedFalseOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    List<Prescription> findByPatientIdAndDeletedFalseOrderByCreatedAtDesc(UUID patientId);

    @Query(value = "SELECT nextval('rx_number_seq')", nativeQuery = true)
    Long getNextRxSeq();
}
