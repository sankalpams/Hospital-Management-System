package com.hms.appointment;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AppointmentDtos {
    private AppointmentDtos() {}

    public record CreateAppointmentRequest(
            @NotNull(message = "Doctor ID is required")
            UUID doctorId,

            @NotNull(message = "Patient ID is required")
            UUID patientId,

            UUID departmentId,

            @NotNull(message = "Appointment date is required")
            @FutureOrPresent(message = "Appointment date cannot be in the past")
            LocalDate appointmentDate,

            @NotNull(message = "Start time is required")
            LocalTime startTime,

            String reason,
            AppointmentType type
    ) {}

    public record WalkInAppointmentRequest(
            @NotNull(message = "Doctor ID is required")
            UUID doctorId,

            @NotNull(message = "Patient ID is required")
            UUID patientId,

            UUID departmentId,

            String reason
    ) {}

    public record RescheduleAppointmentRequest(
            @NotNull(message = "New appointment date is required")
            @FutureOrPresent(message = "Appointment date cannot be in the past")
            LocalDate appointmentDate,

            @NotNull(message = "New start time is required")
            LocalTime startTime,

            @NotBlank(message = "Reschedule reason is required")
            String reason
    ) {}

    public record CancelAppointmentRequest(
            @NotBlank(message = "Cancellation reason is required")
            String reason
    ) {}

    public record AppointmentResponse(
            UUID id,
            String appointmentNumber,
            UUID doctorId,
            String doctorName,
            String doctorSpecialization,
            BigDecimal consultationFee,
            UUID patientId,
            String patientName,
            String patientMrn,
            String patientPhone,
            UUID departmentId,
            String departmentName,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime,
            Integer tokenNumber,
            AppointmentStatus status,
            AppointmentType type,
            String reason,
            String cancellationReason,
            ReminderStatus reminderStatus,
            Long version
    ) {}

    public record AppointmentDetailResponse(
            AppointmentResponse appointment,
            List<AppointmentStatusHistoryResponse> statusHistory
    ) {}

    public record AppointmentStatusHistoryResponse(
            UUID id,
            AppointmentStatus fromStatus,
            AppointmentStatus toStatus,
            String reason,
            String changedBy,
            OffsetDateTime changedAt
    ) {}

    public record CalendarEventResponse(
            String id,
            String title,
            String start,
            String end,
            UUID doctorId,
            String doctorName,
            UUID patientId,
            String patientName,
            String patientMrn,
            AppointmentStatus status,
            Integer tokenNumber
    ) {}

    public record QueueItemResponse(
            UUID id,
            String appointmentNumber,
            Integer tokenNumber,
            UUID patientId,
            String patientName,
            String patientMrn,
            AppointmentStatus status,
            AppointmentType type,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {}

    public record DoctorScheduleDto(
            UUID id,
            UUID doctorId,
            String doctorName,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Integer slotDurationMinutes,
            Integer maxPatients,
            String roomNumber,
            boolean active
    ) {}

    public record CreateDoctorScheduleRequest(
            @NotNull(message = "Day of week is required")
            DayOfWeek dayOfWeek,

            @NotNull(message = "Start time is required")
            LocalTime startTime,

            @NotNull(message = "End time is required")
            LocalTime endTime,

            @NotNull(message = "Slot duration is required")
            Integer slotDurationMinutes,

            @NotNull(message = "Max patients is required")
            Integer maxPatients,

            String roomNumber
    ) {}

    public record ScheduleExceptionDto(
            UUID id,
            UUID doctorId,
            String doctorName,
            ScheduleExceptionType exceptionType,
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            String reason,
            Integer slotDurationMinutes,
            Integer maxPatients
    ) {}

    public record CreateScheduleExceptionRequest(
            @NotNull(message = "Exception type is required")
            ScheduleExceptionType exceptionType,

            @NotNull(message = "Start date time is required")
            OffsetDateTime startDateTime,

            @NotNull(message = "End date time is required")
            OffsetDateTime endDateTime,

            String reason,
            Integer slotDurationMinutes,
            Integer maxPatients
    ) {}

    public record TimeSlotDto(
            LocalTime startTime,
            LocalTime endTime,
            boolean isBooked,
            boolean isBlocked,
            boolean isLeave,
            String status,
            Integer tokenNumber
    ) {}
}

