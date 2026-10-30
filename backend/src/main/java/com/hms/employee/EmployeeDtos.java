package com.hms.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class EmployeeDtos {
    private EmployeeDtos() {}

    public record CreateEmployeeRequest(
            UUID userId,
            Long departmentId,
            @NotBlank @Size(max = 50) String employeeNumber,
            @NotBlank @Size(max = 100) String designation,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Use +94XXXXXXXXX format") String contactPhone
    ) {}

    public record UpdateEmployeeRequest(
            UUID userId,
            Long departmentId,
            @NotBlank @Size(max = 100) String designation,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Use +94XXXXXXXXX format") String contactPhone,
            Boolean active
    ) {}

    public record EmployeeResponse(
            UUID id,
            UUID userId,
            String employeeName,
            String username,
            String email,
            Long departmentId,
            String departmentName,
            String employeeNumber,
            String designation,
            String contactPhone,
            boolean active
    ) {}
}

