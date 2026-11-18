package lk.hms.modules.inpatient.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InpatientDtos {

    private InpatientDtos() {
    }

    public record WardRequest(@NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Letters, numbers and hyphens only.") String code,
                              @NotBlank @Size(max = 100) String name,
                              @NotNull Long departmentId,
                              @NotNull @Pattern(regexp = "GENERAL|ICU|PAEDIATRIC|MATERNITY|SURGICAL|PRIVATE") String wardType,
                              @NotNull @Pattern(regexp = "MALE|FEMALE|MIXED") String genderPolicy,
                              @Size(max = 10) String floor) {
    }

    public record RoomRequest(@NotBlank @Size(max = 20) String roomNo,
                              @NotNull @Pattern(regexp = "SHARED|PRIVATE|ISOLATION|ICU") String roomType,
                              @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal dailyRate,
                              @NotNull @Min(1) @Max(20) Integer beds) {
    }

    public record BedStatusRequest(@NotNull @Pattern(regexp = "AVAILABLE|CLEANING|MAINTENANCE", message = "Beds become occupied only by admitting a patient.") String status) {
    }

    public record AdmissionRequest(@NotNull Long patientId, @NotNull Long attendingDoctorId,
                                   @NotNull @Pattern(regexp = "ELECTIVE|EMERGENCY|TRANSFER_IN") String admissionType,
                                   @NotBlank @Size(max = 500) String reason) {
    }

    public record BedRequest(@NotNull Long bedId) {
    }

    public record DischargeRequest(@NotNull @Pattern(regexp = "ROUTINE|AGAINST_ADVICE|TRANSFERRED|DECEASED") String dischargeType,
                                   @NotBlank @Size(min = 10, max = 10000) String summary) {
    }

    public record ReasonRequest(@NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record BedView(Long id, String bedNo, String status, Long admissionId, Long patientId, String patientName, String patientMrn) {
    }

    public record RoomView(Long id, String roomNo, String roomType, BigDecimal dailyRate, List<BedView> beds) {
    }

    public record WardView(Long id, String code, String name, Long departmentId, String departmentName, String wardType,
                           String genderPolicy, String floor, boolean active, int totalBeds, int occupied, int available,
                           List<RoomView> rooms) {
    }

    public record StayView(String wardName, String roomNo, String bedNo, Instant assignedAt, Instant releasedAt, String releaseReason) {
    }

    public record AdmissionView(Long id, String admissionNo, Long patientId, String patientName, String patientMrn, String patientGender,
                                Long attendingDoctorId, String doctorName, String departmentName, String admissionType, String status,
                                String reason, Instant requestedAt, Instant admittedAt, Instant dischargedAt, String dischargeType,
                                String dischargeSummary, String currentBed, List<StayView> stays) {
    }
}
