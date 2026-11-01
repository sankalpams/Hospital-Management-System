package com.hms.appointment;

import com.hms.common.AppException;
import com.hms.doctor.Doctor;
import com.hms.doctor.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotGenerationService {

    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleExceptionRepository exceptionRepository;
    private final AppointmentRepository appointmentRepository;

    private static final ZoneId COLOMBO_ZONE = ZoneId.of("Asia/Colombo");

    public List<AppointmentDtos.TimeSlotDto> generateSlots(UUID doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> AppException.notFound("Doctor not found: " + doctorId));

        OffsetDateTime startOfDay = date.atStartOfDay(COLOMBO_ZONE).toOffsetDateTime();
        OffsetDateTime endOfDay = date.atTime(LocalTime.MAX).atZone(COLOMBO_ZONE).toOffsetDateTime();

        List<ScheduleException> exceptions = exceptionRepository.findOverlappingExceptions(doctorId, startOfDay, endOfDay);

        // Check for LEAVE exception
        boolean onLeave = exceptions.stream()
                .anyMatch(e -> e.getExceptionType() == ScheduleExceptionType.LEAVE);

        if (onLeave) {
            return Collections.emptyList();
        }

        // Check for EXTRA_SESSION or fallback to weekly schedule
        Optional<ScheduleException> extraSession = exceptions.stream()
                .filter(e -> e.getExceptionType() == ScheduleExceptionType.EXTRA_SESSION)
                .findFirst();

        LocalTime startTime;
        LocalTime endTime;
        int slotDuration;

        if (extraSession.isPresent()) {
            ScheduleException extra = extraSession.get();
            startTime = extra.getStartDateTime().atZoneSameInstant(COLOMBO_ZONE).toLocalTime();
            endTime = extra.getEndDateTime().atZoneSameInstant(COLOMBO_ZONE).toLocalTime();
            slotDuration = extra.getSlotDurationMinutes() != null ? extra.getSlotDurationMinutes() : 15;
        } else {
            Optional<DoctorSchedule> weeklySchedule = scheduleRepository
                    .findByDoctorIdAndDayOfWeekAndActiveTrue(doctorId, date.getDayOfWeek());

            if (weeklySchedule.isEmpty()) {
                return Collections.emptyList();
            }

            DoctorSchedule sched = weeklySchedule.get();
            startTime = sched.getStartTime();
            endTime = sched.getEndTime();
            slotDuration = sched.getSlotDurationMinutes();
        }

        if (slotDuration <= 0) {
            slotDuration = 15;
        }

        // Fetch existing active appointments for that date
        List<Appointment> existingAppointments = appointmentRepository.findByDoctorIdAndAppointmentDateAndStatusNotIn(
                doctorId,
                date,
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED)
        );

        Map<LocalTime, Appointment> bookedMap = new HashMap<>();
        for (Appointment apt : existingAppointments) {
            bookedMap.put(apt.getStartTime(), apt);
        }

        // List blocked slot exceptions
        List<ScheduleException> blockedExceptions = exceptions.stream()
                .filter(e -> e.getExceptionType() == ScheduleExceptionType.BLOCKED_SLOT)
                .toList();

        List<AppointmentDtos.TimeSlotDto> slots = new ArrayList<>();
        LocalTime current = startTime;

        while (current.plusMinutes(slotDuration).isBefore(endTime) || current.plusMinutes(slotDuration).equals(endTime)) {
            LocalTime slotEnd = current.plusMinutes(slotDuration);
            OffsetDateTime slotStartOffset = date.atTime(current).atZone(COLOMBO_ZONE).toOffsetDateTime();
            OffsetDateTime slotEndOffset = date.atTime(slotEnd).atZone(COLOMBO_ZONE).toOffsetDateTime();

            boolean isBlocked = blockedExceptions.stream().anyMatch(e ->
                    slotStartOffset.isBefore(e.getEndDateTime()) && slotEndOffset.isAfter(e.getStartDateTime())
            );

            Appointment apt = bookedMap.get(current);
            boolean isBooked = (apt != null);
            String status = isBooked ? apt.getStatus().name() : (isBlocked ? "BLOCKED" : "AVAILABLE");
            Integer token = isBooked ? apt.getTokenNumber() : null;

            slots.add(new AppointmentDtos.TimeSlotDto(
                    current,
                    slotEnd,
                    isBooked,
                    isBlocked,
                    false,
                    status,
                    token
            ));

            current = slotEnd;
        }

        return slots;
    }
}

