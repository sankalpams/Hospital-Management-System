package lk.hms.modules.emr.repository;

import lk.hms.modules.emr.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {

    List<VitalSign> findTop20ByPatientIdOrderByRecordedAtDesc(Long patientId);
}
