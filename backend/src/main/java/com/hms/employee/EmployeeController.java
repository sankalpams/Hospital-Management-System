package com.hms.employee;

import com.hms.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_READ', 'ROLE_READ')")
    public PageResponse<EmployeeDtos.EmployeeResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "employeeNumber,asc") String sort
    ) {
        return employeeService.list(q, departmentId, active, page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_READ', 'ROLE_READ')")
    public EmployeeDtos.EmployeeResponse get(@PathVariable UUID id) {
        return employeeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public EmployeeDtos.EmployeeResponse create(@Valid @RequestBody EmployeeDtos.CreateEmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public EmployeeDtos.EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody EmployeeDtos.UpdateEmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void delete(@PathVariable UUID id) {
        employeeService.delete(id);
    }
}

