package com.hms.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, UUID> {
    List<PatientDocument> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
    Optional<PatientDocument> findByIdAndPatientId(UUID id, UUID patientId);
    void deleteByPatientId(UUID patientId);
}

