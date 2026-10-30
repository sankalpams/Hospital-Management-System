package com.hms.doctor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class DoctorDtos {
    private DoctorDtos() {}

    public record CreateDoctorRequest(
            @NotNull UUID userId,
            @NotNull Long departmentId,
            @NotBlank @Size(max = 100) String specialization,
            @NotBlank @Size(max = 200) String qualification,
            @NotBlank @Size(max = 50) String slmcRegNumber,
            @NotNull @DecimalMin("0.00") BigDecimal consultationFee
    ) {}

    public record UpdateDoctorRequest(
            @NotNull Long departmentId,
            @NotBlank @Size(max = 100) String specialization,
            @NotBlank @Size(max = 200) String qualification,
            @NotNull @DecimalMin("0.00") BigDecimal consultationFee,
            Boolean active
    ) {}

    public record DoctorResponse(
            UUID id,
            UUID userId,
            String doctorName,
            String email,
            String phone,
            Long departmentId,
            String departmentName,
            String specialization,
            String qualification,
            String slmcRegNumber,
            BigDecimal consultationFee,
            boolean active
    ) {}
}

