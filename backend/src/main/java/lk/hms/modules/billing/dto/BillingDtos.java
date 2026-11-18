package lk.hms.modules.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {
    }

    public record ServiceChargeRequest(@NotNull Long patientId, @NotNull Long serviceId,
                                       @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 6, fraction = 2) BigDecimal quantity) {
    }

    public record CreateInvoiceRequest(@NotNull Long patientId, @NotEmpty List<Long> chargeIds,
                                       @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal discountAmount,
                                       @Size(max = 255) String discountReason) {
    }

    public record PaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                 @NotNull @Pattern(regexp = "CASH|CARD|BANK_TRANSFER|CHEQUE") String method,
                                 @Size(max = 60) String referenceNo,
                                 @Size(max = 255) String notes) {
    }

    public record RefundRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                @NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record ReasonRequest(@NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record ChargeView(Long id, String category, String description, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal amount, String sourceType, Long sourceId, Instant serviceDate, Long invoiceId) {
    }

    public record ServiceView(Long id, String code, String name, String category, BigDecimal price) {
    }

    public record InvoiceLine(Long id, String category, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record PaymentView(Long id, String receiptNo, BigDecimal amount, String method, String referenceNo, String notes,
                              Long refundOfPaymentId, BigDecimal refundable, String receivedByName, Instant receivedAt) {
    }

    public record InvoiceView(Long id, String invoiceNo, Long patientId, String patientMrn, String patientName, String status,
                              BigDecimal subtotal, BigDecimal discountAmount, String discountReason, BigDecimal taxAmount,
                              BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balanceDue, Instant issuedAt,
                              String voidReason, Instant createdAt, List<InvoiceLine> items, List<PaymentView> payments) {
    }
}
