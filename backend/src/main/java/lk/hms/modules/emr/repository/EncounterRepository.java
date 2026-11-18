package lk.hms.modules.emr.repository;

import lk.hms.modules.emr.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    Optional<Encounter> findByAppointmentId(Long appointmentId);

    List<Encounter> findByPatientIdOrderByStartedAtDesc(Long patientId);

    boolean existsByPatientIdAndDoctorId(Long patientId, Long doctorId);

    Optional<Encounter> findFirstByAdmissionIdAndStatus(Long admissionId, Encounter.Status status);
}
