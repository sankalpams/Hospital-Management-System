package com.hms.appointment;

import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import com.hms.security.CurrentUser;
import com.hms.security.HmsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Appointment booking, status transitions, daily queue, and calendar endpoints")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Search and list appointments with filters and pagination")
    public PageResponse<AppointmentDtos.AppointmentResponse> searchAppointments(
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "appointmentDate,desc") String sort,
            @CurrentUser HmsPrincipal principal
    ) {
        Pageable pageable = PageableFactory.create(page, size, sort);
        return appointmentService.searchAppointments(doctorId, departmentId, patientId, startDate, endDate, status, q, pageable, principal);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get calendar events formatted for FullCalendar")
    public List<AppointmentDtos.CalendarEventResponse> getCalendarEvents(
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.getCalendarEvents(doctorId, departmentId, start, end, principal);
    }

    @GetMapping("/queue/today")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get today's clinic queue in token order")
    public List<AppointmentDtos.QueueItemResponse> getTodayQueue(
            @RequestParam(required = false) UUID doctorId,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.getTodayQueue(doctorId, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    @Operation(summary = "Get appointment details including full status history audit trail")
    public AppointmentDtos.AppointmentDetailResponse getAppointment(
            @PathVariable UUID id,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.getAppointment(id, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Book a new standard appointment with double-booking prevention")
    public AppointmentDtos.AppointmentResponse bookAppointment(
            @Valid @RequestBody AppointmentDtos.CreateAppointmentRequest request,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.bookAppointment(request, principal);
    }

    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Register and check-in a walk-in patient with the next token number")
    public AppointmentDtos.AppointmentResponse bookWalkIn(
            @Valid @RequestBody AppointmentDtos.WalkInAppointmentRequest request,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.bookWalkIn(request, principal);
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Check in patient on arrival")
    public AppointmentDtos.AppointmentResponse checkIn(
            @PathVariable UUID id,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.checkIn(id, principal);
    }

    @PostMapping("/{id}/start-consultation")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Doctor initiates clinical consultation")
    public AppointmentDtos.AppointmentResponse startConsultation(
            @PathVariable UUID id,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.startConsultation(id, principal);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Doctor completes consultation")
    public AppointmentDtos.AppointmentResponse completeConsultation(
            @PathVariable UUID id,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.completeConsultation(id, principal);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Cancel appointment with mandatory cancellation reason")
    public AppointmentDtos.AppointmentResponse cancelAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentDtos.CancelAppointmentRequest request,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.cancelAppointment(id, request, principal);
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('APPOINTMENT_WRITE')")
    @Operation(summary = "Reschedule appointment to a new date and time slot")
    public AppointmentDtos.AppointmentResponse rescheduleAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentDtos.RescheduleAppointmentRequest request,
            @CurrentUser HmsPrincipal principal
    ) {
        return appointmentService.rescheduleAppointment(id, request, principal);
    }
}

