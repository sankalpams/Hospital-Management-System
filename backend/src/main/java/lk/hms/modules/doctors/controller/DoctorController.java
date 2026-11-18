package lk.hms.modules.doctors.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.doctors.dto.DoctorDtos.DoctorOption;
import lk.hms.modules.doctors.dto.DoctorDtos.DoctorRequest;
import lk.hms.modules.doctors.dto.DoctorDtos.DoctorResponse;
import lk.hms.modules.doctors.dto.DoctorDtos.ExceptionRequest;
import lk.hms.modules.doctors.dto.DoctorDtos.ExceptionResponse;
import lk.hms.modules.doctors.dto.DoctorDtos.ScheduleRequest;
import lk.hms.modules.doctors.dto.DoctorDtos.ScheduleResponse;
import lk.hms.modules.doctors.service.DoctorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {

    private final DoctorService service;

    public DoctorController(DoctorService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('doctor:read')")
    public PageResponse<DoctorResponse> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Long departmentId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        return service.search(q, departmentId, status, page, size, sort);
    }

    /** Doctors that can currently be booked, optionally within one department. */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('doctor:read')")
    public List<DoctorOption> options(@RequestParam(required = false) Long departmentId) {
        return service.bookable(departmentId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('doctor:read')")
    public DoctorResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('doctor:manage')")
    public DoctorResponse create(@Valid @RequestBody DoctorRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('doctor:manage')")
    public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody DoctorRequest body) {
        return service.update(id, body);
    }

    @GetMapping("/{id}/schedules")
    @PreAuthorize("hasAuthority('doctor:read')")
    public List<ScheduleResponse> schedules(@PathVariable Long id) {
        return service.schedules(id);
    }

    @PostMapping("/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('schedule:manage')")
    public ScheduleResponse addSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest body) {
        return service.addSchedule(id, body);
    }

    @PutMapping("/{id}/schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('schedule:manage')")
    public ScheduleResponse updateSchedule(@PathVariable Long id, @PathVariable Long scheduleId,
                                           @Valid @RequestBody ScheduleRequest body) {
        return service.updateSchedule(id, scheduleId, body);
    }

    @GetMapping("/{id}/exceptions")
    @PreAuthorize("hasAuthority('doctor:read')")
    public List<ExceptionResponse> exceptions(@PathVariable Long id) {
        return service.exceptions(id);
    }

    @PostMapping("/{id}/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('schedule:manage')")
    public ExceptionResponse addException(@PathVariable Long id, @Valid @RequestBody ExceptionRequest body) {
        return service.addException(id, body);
    }

    @DeleteMapping("/{id}/exceptions/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('schedule:manage')")
    public void removeException(@PathVariable Long id, @PathVariable Long exceptionId) {
        service.removeException(id, exceptionId);
    }
}
