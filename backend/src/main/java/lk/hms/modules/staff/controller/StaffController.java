package lk.hms.modules.staff.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.staff.dto.StaffDtos.*;
import lk.hms.modules.staff.service.StaffService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('employee:read')")
    public PageResponse<EmployeeView> employees(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) Long departmentId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        return service.search(q, category, departmentId, status, page, size, sort);
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('employee:read')")
    public EmployeeView employee(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('employee:manage')")
    public EmployeeView create(@Valid @RequestBody EmployeeRequest body) {
        return service.create(body);
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('employee:manage')")
    public EmployeeView update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest body) {
        return service.update(id, body);
    }

    @GetMapping("/employees/{id}/attendance")
    @PreAuthorize("hasAuthority('employee:read')")
    public List<AttendanceDay> history(@PathVariable Long id,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(id, from, to);
    }

    @GetMapping("/employees/{id}/leave-balances")
    @PreAuthorize("hasAuthority('employee:read')")
    public List<LeaveBalance> balances(@PathVariable Long id, @RequestParam int year) {
        return service.balances(id, year);
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('attendance:manage')")
    public List<AttendanceRow> register(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.register(date);
    }

    @PutMapping("/attendance")
    @PreAuthorize("hasAuthority('attendance:manage')")
    public List<AttendanceRow> saveRegister(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            @Valid @RequestBody AttendanceSave body) {
        return service.saveRegister(date, body);
    }

    @GetMapping("/leave-types")
    public List<LeaveTypeView> leaveTypes() {
        return service.leaveTypes();
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyAuthority('leave:approve','employee:manage')")
    public PageResponse<LeaveView> leave(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long employeeId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.searchLeave(status, employeeId, page, size);
    }

    /** Self-service: any signed-in user; the service checks it is their own record unless they are HR. */
    @PostMapping("/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveView apply(@Valid @RequestBody LeaveApply body) {
        return service.apply(body);
    }

    @PostMapping("/leave-requests/{id}/approve")
    @PreAuthorize("hasAuthority('leave:approve')")
    public LeaveView approve(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest body) {
        return service.approve(id, body == null ? null : body.note());
    }

    @PostMapping("/leave-requests/{id}/reject")
    @PreAuthorize("hasAuthority('leave:approve')")
    public LeaveView reject(@PathVariable Long id, @Valid @RequestBody DecisionRequest body) {
        return service.reject(id, body.note());
    }

    @PostMapping("/leave-requests/{id}/cancel")
    public LeaveView cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @GetMapping("/me/leave")
    public MyLeave myLeave() {
        return service.myLeave();
    }
}
