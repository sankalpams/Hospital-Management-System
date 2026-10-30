package com.hms.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, UUID> {
    List<PatientAllergy> findByPatientIdOrderBySeverityDesc(UUID patientId);
    void deleteByPatientId(UUID patientId);
}

