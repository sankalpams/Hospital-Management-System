package lk.hms.modules.appointments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.hms.modules.appointments.entity.Appointment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /** date + time are hospital-local; the server converts to UTC. */
    public record BookRequest(@NotNull Long patientId, @NotNull Long doctorId, @NotNull LocalDate date,
                              @NotNull LocalTime time, Appointment.Type type, @Size(max = 255) String reason,
                              boolean confirmNow) {
    }

    public record WalkInRequest(@NotNull Long patientId, @NotNull Long doctorId, @Size(max = 255) String reason) {
    }

    public record RescheduleRequest(@NotNull Long doctorId, @NotNull LocalDate date, @NotNull LocalTime time,
                                    @Size(max = 255) String reason) {
    }

    public record ReasonRequest(@NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record AppointmentResponse(Long id, String appointmentNo, Instant slotStart, Instant slotEnd,
                                      LocalDate localDate, LocalTime localTime, int tokenNo, String type, String status,
                                      String reason, String cancelReason, Long rescheduledFromId,
                                      Long patientId, String patientMrn, String patientName, String patientPhone,
                                      Long doctorId, String doctorName, String specialization,
                                      Long departmentId, String departmentName, BigDecimal consultationFee,
                                      Instant checkedInAt, Instant startedAt, Instant completedAt, Instant createdAt,
                                      List<String> allowedActions, long version) {
    }

    public record Slot(LocalTime time, Instant startsAt, boolean available, String unavailableReason) {
    }

    public record Session(Long scheduleId, LocalTime startTime, LocalTime endTime, String roomLabel,
                          int capacity, int booked, List<Slot> slots) {
    }

    public record Availability(Long doctorId, String doctorName, LocalDate date, String blockedReason,
                               List<Session> sessions) {
    }
}
