package com.hms.employee;

import com.hms.audit.AuditAction;
import com.hms.audit.Audited;
import com.hms.common.AppException;
import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import com.hms.department.Department;
import com.hms.department.DepartmentService;
import com.hms.user.User;
import com.hms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeRepository employees;
    private final DepartmentService departmentService;
    private final UserRepository users;
    private final EmployeeMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDtos.EmployeeResponse> list(String q, Long departmentId, Boolean active, int page, int size, String sort) {
        Page<EmployeeDtos.EmployeeResponse> result = employees.search(
                blank(q), departmentId, active, PageableFactory.of(page, size, sort)
        ).map(mapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public EmployeeDtos.EmployeeResponse get(UUID id) {
        return mapper.toResponse(required(id));
    }

    @Audited(action = AuditAction.CREATE, entityType = "Employee")
    @Transactional
    public EmployeeDtos.EmployeeResponse create(EmployeeDtos.CreateEmployeeRequest request) {
        String empNum = request.employeeNumber().trim().toUpperCase();
        if (employees.existsByEmployeeNumberIgnoreCase(empNum)) {
            throw AppException.conflict("An employee with number " + empNum + " already exists.");
        }

        User user = null;
        if (request.userId() != null) {
            user = users.findByIdAndDeletedAtIsNull(request.userId())
                    .orElseThrow(() -> AppException.notFound("User account not found."));
            if (employees.existsByUserId(request.userId())) {
                throw AppException.conflict("An employee profile already exists for this user account.");
            }
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentService.required(request.departmentId());
        }

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setDepartment(department);
        employee.setEmployeeNumber(empNum);
        employee.setDesignation(request.designation().trim());
        employee.setContactPhone(blank(request.contactPhone()));
        employee.setActive(true);

        return mapper.toResponse(employees.save(employee));
    }

    @Audited(action = AuditAction.UPDATE, entityType = "Employee", entityIdArgument = 0)
    @Transactional
    public EmployeeDtos.EmployeeResponse update(UUID id, EmployeeDtos.UpdateEmployeeRequest request) {
        Employee employee = required(id);

        if (request.userId() != null && (employee.getUser() == null || !employee.getUser().getId().equals(request.userId()))) {
            User user = users.findByIdAndDeletedAtIsNull(request.userId())
                    .orElseThrow(() -> AppException.notFound("User account not found."));
            employee.setUser(user);
        } else if (request.userId() == null) {
            employee.setUser(null);
        }

        if (request.departmentId() != null) {
            Department department = departmentService.required(request.departmentId());
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null);
        }

        employee.setDesignation(request.designation().trim());
        employee.setContactPhone(blank(request.contactPhone()));
        if (request.active() != null) {
            employee.setActive(request.active());
        }

        return mapper.toResponse(employee);
    }

    @Audited(action = AuditAction.DELETE, entityType = "Employee", entityIdArgument = 0)
    @Transactional
    public void delete(UUID id) {
        Employee employee = required(id);
        employee.setActive(false);
    }

    public Employee required(UUID id) {
        return employees.findById(id).orElseThrow(() -> AppException.notFound("Employee not found."));
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

