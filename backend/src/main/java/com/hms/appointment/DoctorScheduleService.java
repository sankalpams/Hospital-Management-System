package com.hms.appointment;

import com.hms.common.AppException;
import com.hms.doctor.Doctor;
import com.hms.doctor.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleExceptionRepository exceptionRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentMapper mapper;

    @Transactional(readOnly = true)
    public List<AppointmentDtos.DoctorScheduleDto> getDoctorSchedules(UUID doctorId) {
        return scheduleRepository.findByDoctorIdAndActiveTrue(doctorId)
                .stream()
                .map(mapper::toScheduleDto)
                .toList();
    }

    @Transactional
    public AppointmentDtos.DoctorScheduleDto createOrUpdateSchedule(UUID doctorId, AppointmentDtos.CreateDoctorScheduleRequest req) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> AppException.notFound("Doctor not found: " + doctorId));

        if (req.startTime().isAfter(req.endTime()) || req.startTime().equals(req.endTime())) {
            throw AppException.badRequest("Schedule start time must be before end time");
        }

        Optional<DoctorSchedule> existing = scheduleRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(doctorId, req.dayOfWeek());
        DoctorSchedule schedule;
        if (existing.isPresent()) {
            schedule = existing.get();
            schedule.setStartTime(req.startTime());
            schedule.setEndTime(req.endTime());
            schedule.setSlotDurationMinutes(req.slotDurationMinutes());
            schedule.setMaxPatients(req.maxPatients());
            schedule.setRoomNumber(req.roomNumber());
        } else {
            schedule = new DoctorSchedule(
                    doctor,
                    req.dayOfWeek(),
                    req.startTime(),
                    req.endTime(),
                    req.slotDurationMinutes(),
                    req.maxPatients(),
                    req.roomNumber()
            );
        }

        schedule = scheduleRepository.save(schedule);
        return mapper.toScheduleDto(schedule);
    }

    @Transactional
    public void deleteSchedule(UUID scheduleId) {
        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> AppException.notFound("Schedule not found: " + scheduleId));
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.ScheduleExceptionDto> getDoctorExceptions(UUID doctorId) {
        return exceptionRepository.findByDoctorId(doctorId)
                .stream()
                .map(mapper::toExceptionDto)
                .toList();
    }

    @Transactional
    public AppointmentDtos.ScheduleExceptionDto createException(UUID doctorId, AppointmentDtos.CreateScheduleExceptionRequest req) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> AppException.notFound("Doctor not found: " + doctorId));

        if (req.startDateTime().isAfter(req.endDateTime()) || req.startDateTime().isEqual(req.endDateTime())) {
            throw AppException.badRequest("Exception start time must be before end time");
        }

        ScheduleException exception = new ScheduleException(
                doctor,
                req.exceptionType(),
                req.startDateTime(),
                req.endDateTime(),
                req.reason(),
                req.slotDurationMinutes(),
                req.maxPatients()
        );

        exception = exceptionRepository.save(exception);
        return mapper.toExceptionDto(exception);
    }

    @Transactional
    public void deleteException(UUID exceptionId) {
        if (!exceptionRepository.existsById(exceptionId)) {
            throw AppException.notFound("Exception not found: " + exceptionId);
        }
        exceptionRepository.deleteById(exceptionId);
    }
}

