package lk.hms.modules.lab.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

public final class LabDtos {

    private LabDtos() {
    }

    public record LabTestRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Letters, numbers and hyphens only.") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String category,
            @NotNull @Pattern(regexp = "BLOOD|URINE|STOOL|SWAB|SPUTUM|CSF|TISSUE|OTHER") String sampleType,
            @NotNull @Pattern(regexp = "NUMERIC|TEXT") String resultType,
            @Size(max = 20) String unit,
            @Digits(integer = 9, fraction = 3) BigDecimal refRangeLow,
            @Digits(integer = 9, fraction = 3) BigDecimal refRangeHigh,
            @Size(max = 120) String refRangeText,
            @NotNull @Min(1) @Max(720) Integer turnaroundHours,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Boolean active) {
    }

    public record OrderRequest(@NotEmpty @Size(max = 30) List<Long> testIds,
                               @NotNull @Pattern(regexp = "ROUTINE|URGENT|STAT") String priority,
                               @Size(max = 500) String clinicalNotes) {
    }

    public record ResultRequest(@Digits(integer = 9, fraction = 3) BigDecimal numericValue,
                                @Size(max = 1000) String textValue,
                                @Size(max = 500) String comments) {
    }

    public record ReasonRequest(@NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record LabTestView(Long id, String code, String name, String category, String sampleType, String resultType,
                              String unit, BigDecimal refRangeLow, BigDecimal refRangeHigh, String refRangeText,
                              int turnaroundHours, BigDecimal price, boolean active) {
    }

    public record ResultView(BigDecimal numericValue, String textValue, String unit, BigDecimal refRangeLow,
                             BigDecimal refRangeHigh, String flag, String comments, String enteredByName, Instant enteredAt) {
    }

    public record ItemView(Long id, Long labTestId, String testCode, String testName, String sampleType, String status,
                           BigDecimal price, String sampleBarcode, Instant sampleCollectedAt, Instant completedAt,
                           Instant reviewedAt, String cancelReason, Instant dueAt, ResultView result) {
    }

    public record RequestView(Long id, String requestNo, Long patientId, String patientMrn, String patientName,
                              Long encounterId, String doctorName, String priority, String clinicalNotes,
                              Instant requestedAt, String status, List<ItemView> items) {
    }
}
