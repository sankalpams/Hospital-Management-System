package lk.hms.modules.appointments.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.appointments.dto.AppointmentDtos.AppointmentResponse;
import lk.hms.modules.appointments.dto.AppointmentDtos.Availability;
import lk.hms.modules.appointments.dto.AppointmentDtos.BookRequest;
import lk.hms.modules.appointments.dto.AppointmentDtos.ReasonRequest;
import lk.hms.modules.appointments.dto.AppointmentDtos.RescheduleRequest;
import lk.hms.modules.appointments.dto.AppointmentDtos.WalkInRequest;
import lk.hms.modules.appointments.service.AppointmentService;
import lk.hms.modules.appointments.service.AppointmentService.DayCount;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping("/doctors/{doctorId}/availability")
    @PreAuthorize("hasAuthority('appointment:read')")
    public Availability availability(@PathVariable Long doctorId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.availability(doctorId, date);
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasAuthority('appointment:read')")
    public PageResponse<AppointmentResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        return service.search(from, to, doctorId, departmentId, patientId, status, q, mine, page, size, sort);
    }

    @GetMapping("/appointments/calendar")
    @PreAuthorize("hasAuthority('appointment:read')")
    public List<DayCount> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long doctorId,
                                   @RequestParam(required = false) Long departmentId) {
        return service.calendar(from, to, doctorId, departmentId);
    }

    @GetMapping("/appointments/{id}")
    @PreAuthorize("hasAuthority('appointment:read')")
    public AppointmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('appointment:create')")
    public AppointmentResponse book(@Valid @RequestBody BookRequest body) {
        return service.book(body);
    }

    @PostMapping("/appointments/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('appointment:create') and hasAuthority('appointment:checkin')")
    public AppointmentResponse walkIn(@Valid @RequestBody WalkInRequest body) {
        return service.walkIn(body);
    }

    @PostMapping("/appointments/{id}/confirm")
    @PreAuthorize("hasAuthority('appointment:update')")
    public AppointmentResponse confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PostMapping("/appointments/{id}/reschedule")
    @PreAuthorize("hasAuthority('appointment:update')")
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest body) {
        return service.reschedule(id, body);
    }

    @PostMapping("/appointments/{id}/cancel")
    @PreAuthorize("hasAuthority('appointment:update')")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        return service.cancel(id, body.reason());
    }

    @PostMapping("/appointments/{id}/no-show")
    @PreAuthorize("hasAuthority('appointment:update')")
    public AppointmentResponse noShow(@PathVariable Long id) {
        return service.noShow(id);
    }

    @PostMapping("/appointments/{id}/check-in")
    @PreAuthorize("hasAuthority('appointment:checkin')")
    public AppointmentResponse checkIn(@PathVariable Long id) {
        return service.checkIn(id);
    }

    @PostMapping("/appointments/{id}/start")
    @PreAuthorize("hasAuthority('appointment:consult')")
    public AppointmentResponse start(@PathVariable Long id) {
        return service.start(id);
    }

    @PostMapping("/appointments/{id}/complete")
    @PreAuthorize("hasAuthority('appointment:consult')")
    public AppointmentResponse complete(@PathVariable Long id) {
        return service.complete(id);
    }
}
