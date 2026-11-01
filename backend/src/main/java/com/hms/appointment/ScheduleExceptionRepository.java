package com.hms.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, UUID> {
    List<ScheduleException> findByDoctorId(UUID doctorId);

    @Query("SELECT e FROM ScheduleException e WHERE e.doctor.id = :doctorId AND e.startDateTime < :end AND e.endDateTime > :start")
    List<ScheduleException> findOverlappingExceptions(
            @Param("doctorId") UUID doctorId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );
}

