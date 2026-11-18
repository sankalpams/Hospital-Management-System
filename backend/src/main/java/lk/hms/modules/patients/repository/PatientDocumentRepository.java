package lk.hms.modules.patients.repository;

import lk.hms.modules.patients.entity.PatientDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Long> {

    List<PatientDocument> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
