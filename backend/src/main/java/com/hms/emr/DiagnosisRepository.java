package com.hms.emr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiagnosisRepository extends JpaRepository<Diagnosis, UUID> {

    List<Diagnosis> findByEncounterIdAndDeletedFalseOrderByCreatedAtAsc(UUID encounterId);

    Page<Diagnosis> findByPatientIdAndDeletedFalseOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    List<Diagnosis> findByPatientIdAndDeletedFalseOrderByCreatedAtDesc(UUID patientId);
}
