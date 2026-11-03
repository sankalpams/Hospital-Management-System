package com.hms.emr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VitalsRepository extends JpaRepository<Vitals, UUID> {

    List<Vitals> findByEncounterIdAndDeletedFalseOrderByRecordedAtDesc(UUID encounterId);

    Page<Vitals> findByPatientIdAndDeletedFalseOrderByRecordedAtDesc(UUID patientId, Pageable pageable);

    List<Vitals> findByPatientIdAndDeletedFalseOrderByRecordedAtDesc(UUID patientId);

    Optional<Vitals> findFirstByPatientIdAndDeletedFalseOrderByRecordedAtDesc(UUID patientId);
}
