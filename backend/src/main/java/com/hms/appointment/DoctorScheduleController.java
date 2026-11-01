package com.hms.appointment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Doctor Schedules", description = "Doctor weekly schedule templates, slot generation, and leave exceptions")
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;
    private final SlotGenerationService slotService;

    @GetMapping("/doctors/{doctorId}")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get active weekly schedule templates for a doctor")
    public List<AppointmentDtos.DoctorScheduleDto> getDoctorSchedules(@PathVariable UUID doctorId) {
        return scheduleService.getDoctorSchedules(doctorId);
    }

    @PostMapping("/doctors/{doctorId}")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE') or hasAuthority('USER_MANAGE')")
    @Operation(summary = "Create or update weekly schedule template for a doctor")
    public AppointmentDtos.DoctorScheduleDto createOrUpdateSchedule(
            @PathVariable UUID doctorId,
            @Valid @RequestBody AppointmentDtos.CreateDoctorScheduleRequest request
    ) {
        return scheduleService.createOrUpdateSchedule(doctorId, request);
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE') or hasAuthority('USER_MANAGE')")
    @Operation(summary = "Deactivate weekly schedule template")
    public void deleteSchedule(@PathVariable UUID scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
    }

    @GetMapping("/doctors/{doctorId}/slots")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get real-time available and booked slots for a doctor on a specific date")
    public List<AppointmentDtos.TimeSlotDto> getSlots(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return slotService.generateSlots(doctorId, date);
    }

    @GetMapping("/doctors/{doctorId}/exceptions")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get schedule exceptions (leave, blocked slots, extra sessions)")
    public List<AppointmentDtos.ScheduleExceptionDto> getExceptions(@PathVariable UUID doctorId) {
        return scheduleService.getDoctorExceptions(doctorId);
    }

    @PostMapping("/doctors/{doctorId}/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE') or hasAuthority('USER_MANAGE')")
    @Operation(summary = "Create schedule exception (leave, blocked slot, extra session)")
    public AppointmentDtos.ScheduleExceptionDto createException(
            @PathVariable UUID doctorId,
            @Valid @RequestBody AppointmentDtos.CreateScheduleExceptionRequest request
    ) {
        return scheduleService.createException(doctorId, request);
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE') or hasAuthority('USER_MANAGE')")
    @Operation(summary = "Delete schedule exception")
    public void deleteException(@PathVariable UUID exceptionId) {
        scheduleService.deleteException(exceptionId);
    }
}

