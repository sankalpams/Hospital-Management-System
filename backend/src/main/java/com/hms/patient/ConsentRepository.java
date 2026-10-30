package com.hms.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {
    List<Consent> findByPatientIdOrderByGivenDateDesc(UUID patientId);
    Optional<Consent> findByIdAndPatientId(UUID id, UUID patientId);
    void deleteByPatientId(UUID patientId);
}

