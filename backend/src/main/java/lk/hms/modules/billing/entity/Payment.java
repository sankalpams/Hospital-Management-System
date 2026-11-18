package lk.hms.modules.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Refunds are negative rows that reference the original payment. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 20, updatable = false)
    private String receiptNo;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "reference_no", length = 60)
    private String referenceNo;

    @Column(name = "refund_of_payment_id")
    private Long refundOfPaymentId;

    private String notes;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
