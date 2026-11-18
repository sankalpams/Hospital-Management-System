package lk.hms.modules.emr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class EmrDtos {

    private EmrDtos() {
    }

    // ---------- requests ----------

    public record DiagnosisRequest(
            @Pattern(regexp = "^$|^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$", message = "Use ICD-10 format, e.g. J45.9.") String icd10Code,
            @NotBlank @Size(max = 255) String description,
            @NotNull @Pattern(regexp = "PRIMARY|SECONDARY") String type,
            @NotNull @Pattern(regexp = "PROVISIONAL|CONFIRMED|RULED_OUT") String certainty) {
    }

    public record NoteRequest(@NotNull @Pattern(regexp = "DOCTOR_NOTE|TREATMENT") String noteType,
                              @NotBlank @Size(max = 10000) String content) {
    }

    public record TextRequest(@NotBlank @Size(max = 10000) String content) {
    }

    /** Ranges mirror the database CHECKs so users get a field message instead of a conflict. */
    public record VitalsRequest(
            Long encounterId,
            @DecimalMin("25.0") @DecimalMax("45.0") BigDecimal temperatureC,
            @Min(20) @Max(250) Integer pulseBpm,
            @Min(40) @Max(300) Integer systolicMmhg,
            @Min(20) @Max(200) Integer diastolicMmhg,
            @Min(4) @Max(80) Integer respiratoryRate,
            @Min(50) @Max(100) Integer spo2Percent,
            @DecimalMin("0.3") @DecimalMax("400") BigDecimal weightKg,
            @DecimalMin("20") @DecimalMax("260") BigDecimal heightCm,
            @DecimalMin("0.5") @DecimalMax("60") BigDecimal bloodGlucoseMmol,
            @Size(max = 255) String notes) {
    }

    public record AllergyRequest(@NotNull @Pattern(regexp = "DRUG|FOOD|ENVIRONMENT|OTHER") String allergenType,
                                 @NotBlank @Size(max = 120) String allergen,
                                 @Size(max = 255) String reaction,
                                 @NotNull @Pattern(regexp = "MILD|MODERATE|SEVERE") String severity) {
    }

    public record PrescriptionItemRequest(
            @NotNull Long medicineId,
            @NotBlank @Size(max = 40) String dose,
            @NotBlank @Size(max = 40) String frequency,
            @NotBlank @Pattern(regexp = "ORAL|IV|IM|SC|TOPICAL|INHALED|SUBLINGUAL|RECTAL|OTHER") String route,
            @NotNull @Min(1) @Max(365) Integer durationDays,
            @NotNull @Min(1) @Max(10000) Integer quantity,
            @Size(max = 255) String instructions) {
    }

    public record PrescriptionRequest(@NotEmpty @Size(max = 20) List<@Valid PrescriptionItemRequest> items,
                                      @Size(max = 500) String notes,
                                      @Size(max = 255) String allergyOverrideReason) {
    }

    // ---------- responses ----------

    public record DiagnosisView(Long id, String icd10Code, String description, String type, String certainty, Instant createdAt) {
    }

    public record NoteView(Long id, String noteType, String content, Long authorUserId, String authorName,
                           Long parentNoteId, Instant signedAt, Instant createdAt, boolean editable) {
    }

    public record VitalsView(Long id, Long encounterId, Instant recordedAt, String recordedByName, BigDecimal temperatureC,
                             Integer pulseBpm, Integer systolicMmhg, Integer diastolicMmhg, Integer respiratoryRate,
                             Integer spo2Percent, BigDecimal weightKg, BigDecimal heightCm, BigDecimal bmi,
                             BigDecimal bloodGlucoseMmol, String notes) {
    }

    public record AllergyView(Long id, String allergenType, String allergen, String reaction, String severity, String status) {
    }

    public record PrescriptionItemView(Long id, Long medicineId, String medicineName, String dose, String frequency,
                                       String route, int durationDays, int quantityPrescribed, int quantityDispensed,
                                       String instructions) {
    }

    public record PrescriptionView(Long id, Long encounterId, String prescriptionNo, String status, String notes, String allergyOverrideReason,
                                   String doctorName, Instant createdAt, List<PrescriptionItemView> items) {
    }

    public record EncounterView(Long id, String encounterNo, String type, String status, Long appointmentId,
                                Long doctorId, String doctorName, String departmentName, String chiefComplaint,
                                Instant startedAt, Instant closedAt, boolean canWrite,
                                List<DiagnosisView> diagnoses, List<NoteView> notes, List<PrescriptionView> prescriptions) {
    }

    public record EmrView(Long patientId, String mrn, String patientName, List<AllergyView> allergies,
                          List<VitalsView> recentVitals, List<EncounterView> encounters) {
    }

    public record MedicineOption(Long id, String code, String name, String dosageForm, String unit, BigDecimal unitPrice) {
    }
}
