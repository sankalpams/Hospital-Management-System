package com.hms.emr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    Optional<Encounter> findByEncounterNumber(String encounterNumber);

    Optional<Encounter> findByAppointmentId(UUID appointmentId);

    Page<Encounter> findByPatientIdAndDeletedFalseOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    List<Encounter> findByPatientIdAndDeletedFalseOrderByCreatedAtDesc(UUID patientId);

    Page<Encounter> findByDoctorIdAndDeletedFalseOrderByCreatedAtDesc(UUID doctorId, Pageable pageable);

    @Query(value = "SELECT nextval('encounter_number_seq')", nativeQuery = true)
    Long getNextEncounterSeq();

    @Query("SELECT COUNT(e) > 0 FROM Encounter e WHERE e.patient.id = :patientId AND e.doctor.user.id = :userId")
    boolean existsByPatientIdAndDoctorUserId(@Param("patientId") UUID patientId, @Param("userId") UUID userId);
}
