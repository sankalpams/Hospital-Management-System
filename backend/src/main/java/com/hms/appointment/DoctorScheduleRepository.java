package com.hms.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {
    List<DoctorSchedule> findByDoctorIdAndActiveTrue(UUID doctorId);
    List<DoctorSchedule> findByDoctorId(UUID doctorId);
    Optional<DoctorSchedule> findByDoctorIdAndDayOfWeekAndActiveTrue(UUID doctorId, DayOfWeek dayOfWeek);
    boolean existsByDoctorIdAndDayOfWeekAndActiveTrue(UUID doctorId, DayOfWeek dayOfWeek);
}

