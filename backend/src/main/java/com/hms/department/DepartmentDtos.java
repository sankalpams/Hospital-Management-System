package com.hms.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DepartmentDtos {
    private DepartmentDtos() {}

    public record CreateDepartmentRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9_-]{2,30}$", message = "Code must be 2-30 uppercase characters, digits, underscores, or hyphens") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Use +94XXXXXXXXX format") String phone,
            @Size(max = 100) String location
    ) {}

    public record UpdateDepartmentRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Use +94XXXXXXXXX format") String phone,
            @Size(max = 100) String location,
            Boolean active
    ) {}

    public record DepartmentResponse(
            Long id,
            String code,
            String name,
            String description,
            String phone,
            String location,
            boolean active
    ) {}
}

