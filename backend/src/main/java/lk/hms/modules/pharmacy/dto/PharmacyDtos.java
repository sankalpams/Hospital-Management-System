package lk.hms.modules.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lk.hms.modules.patients.dto.PatientDtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PharmacyDtos {

    private PharmacyDtos() {
    }

    public record MedicineRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Letters, numbers and hyphens only.") String code,
            @NotBlank @Size(max = 120) String genericName,
            @Size(max = 120) String brandName,
            @NotNull Long categoryId,
            @NotNull @Pattern(regexp = "TABLET|CAPSULE|SYRUP|SUSPENSION|INJECTION|INFUSION|CREAM|OINTMENT|DROPS|INHALER|SUPPOSITORY|POWDER|OTHER") String dosageForm,
            @NotBlank @Size(max = 40) String strength,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull @Min(0) @Max(1000000) Integer reorderLevel,
            boolean prescriptionRequired,
            Boolean active,
            Long version) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 255) String description) {
    }

    public record SupplierRequest(@NotBlank @Size(max = 120) String name,
                                  @Size(max = 100) String contactPerson,
                                  @NotBlank @Pattern(regexp = PatientDtos.PHONE_REGEX, message = PatientDtos.PHONE_MESSAGE) String phone,
                                  @Email @Size(max = 120) String email,
                                  @Size(max = 255) String address,
                                  @Size(max = 40) String registrationNo,
                                  Boolean active) {
    }

    public record ReceiptRequest(@NotNull Long medicineId, Long supplierId,
                                 @NotBlank @Size(max = 40) String batchNo,
                                 @PastOrPresent LocalDate manufactureDate,
                                 @NotNull LocalDate expiryDate,
                                 @NotNull @Min(1) @Max(1000000) Integer quantity,
                                 @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice) {
    }

    public record AdjustmentRequest(@NotNull Long batchId, @NotNull @Min(-1000000) @Max(1000000) Integer quantity,
                                    @NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record DispenseLine(@NotNull Long prescriptionItemId, @NotNull @Min(1) @Max(100000) Integer quantity) {
    }

    public record DispenseRequest(@NotEmpty List<@Valid DispenseLine> items, @Size(max = 255) String notes) {
    }

    public record MedicineView(Long id, String code, String genericName, String brandName, String name, Long categoryId,
                               String categoryName, String dosageForm, String strength, String unit, BigDecimal unitPrice,
                               int reorderLevel, boolean prescriptionRequired, boolean active, int stockOnHand,
                               boolean lowStock, long version) {
    }

    public record CategoryView(Long id, String name, String description) {
    }

    public record SupplierView(Long id, String name, String contactPerson, String phone, String email, String address,
                               String registrationNo, boolean active) {
    }

    public record BatchView(Long id, Long medicineId, String medicineName, String batchNo, String supplierName, LocalDate manufactureDate,
                            LocalDate expiryDate, LocalDate receivedDate, int quantityReceived, int quantityOnHand,
                            BigDecimal costPrice, String expiryStatus) {
    }

    public record TransactionView(Long id, Instant createdAt, Long medicineId, String medicineName, String batchNo,
                                  String type, int quantity, int balanceAfter, String referenceType, Long referenceId,
                                  String note, String performedByName) {
    }

    public record QueueItem(Long id, Long medicineId, String medicineName, String dose, String frequency, String route,
                            int durationDays, int quantityPrescribed, int quantityDispensed, int remaining,
                            int stockAvailable, String unit, String instructions) {
    }

    public record QueuePrescription(Long id, String prescriptionNo, String status, Long patientId, String patientMrn,
                                    String patientName, String doctorName, String notes, String allergyOverrideReason,
                                    Instant createdAt, List<QueueItem> items) {
    }

    public record DispenseResult(String dispensationNo, String prescriptionStatus, List<String> lines) {
    }

    public record Alerts(List<MedicineView> lowStock, List<BatchView> expiringSoon, List<BatchView> expiredWithStock, int expiryWindowDays) {
    }
}
