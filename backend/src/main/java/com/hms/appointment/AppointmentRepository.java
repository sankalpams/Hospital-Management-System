package com.hms.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByAppointmentNumber(String appointmentNumber);

    boolean existsByDoctorIdAndAppointmentDateAndStartTimeAndStatusNotIn(
            UUID doctorId,
            LocalDate appointmentDate,
            LocalTime startTime,
            Collection<AppointmentStatus> statuses
    );

    Optional<Appointment> findByDoctorIdAndAppointmentDateAndStartTimeAndStatusNotIn(
            UUID doctorId,
            LocalDate appointmentDate,
            LocalTime startTime,
            Collection<AppointmentStatus> statuses
    );

    List<Appointment> findByDoctorIdAndAppointmentDateAndStatusNotIn(
            UUID doctorId,
            LocalDate appointmentDate,
            Collection<AppointmentStatus> statuses
    );

    List<Appointment> findByDoctorIdAndAppointmentDateOrderByTokenNumberAsc(
            UUID doctorId,
            LocalDate appointmentDate
    );

    @Query("SELECT COALESCE(MAX(a.tokenNumber), 0) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date")
    int findMaxTokenNumber(@Param("doctorId") UUID doctorId, @Param("date") LocalDate date);

    List<Appointment> findByAppointmentDateAndReminderStatusAndStatusIn(
            LocalDate date,
            ReminderStatus reminderStatus,
            Collection<AppointmentStatus> statuses
    );

    List<Appointment> findByAppointmentDateLessThanEqualAndStatusIn(
            LocalDate date,
            Collection<AppointmentStatus> statuses
    );
}

