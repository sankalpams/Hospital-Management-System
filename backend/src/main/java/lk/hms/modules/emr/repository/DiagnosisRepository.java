package lk.hms.modules.emr.repository;

import lk.hms.modules.emr.entity.Diagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    List<Diagnosis> findByEncounterIdInOrderByIdAsc(List<Long> encounterIds);

    long countByEncounterId(Long encounterId);
}
